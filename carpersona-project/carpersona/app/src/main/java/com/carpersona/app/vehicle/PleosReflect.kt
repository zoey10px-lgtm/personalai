package com.carpersona.app.vehicle

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Proxy
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.primaryConstructor

/**
 * Pleos SDK 함수를 "이름으로" 부르는 도우미.
 *
 * 왜 이렇게 하나요?
 *  - Pleos SDK 저장소에 접속이 안 되는 컴퓨터에서도 앱이 빌드되게 하려고 (pleosSdk=false).
 *  - SDK가 없는 일반 폰에서는 자동으로 가짜 차로 넘어가게 하려고.
 *  - 공식 샘플 앱(pleos-sample-vehicle-app)의 VehicleApiExplorer 도 같은 방식(kotlin-reflect)을 씁니다.
 *
 * 공식 샘플에서 확인한 호출 모양:
 *     door.getCurrentDoorPosition(area = DoorArea(DoorValue.ROW_1_LEFT),
 *                                 onComplete = { value -> ... }, onFailed = { e -> ... })
 * 이 도우미는 위 모양에 맞춰 area / onComplete / onFailed 를 자동으로 채워 줍니다.
 */
object PleosReflect {
    private const val TAG = "CarPersona/Pleos"
    private val main = Handler(Looper.getMainLooper())

    /** 클래스가 있으면 KClass, 없으면 null (SDK가 APK에 안 들어 있으면 null) */
    fun classOrNull(name: String): KClass<*>? =
        try { Class.forName(name).kotlin } catch (_: Throwable) { null }

    /** 객체에서 이름이 [name] 인 함수 찾기. 정확한 이름이 없으면 [fallback] 조건으로 찾기 */
    fun findFunction(target: Any, name: String, fallback: ((String) -> Boolean)? = null): KFunction<*>? {
        val all = functionsOf(target)
        return all.firstOrNull { it.name == name } ?: fallback?.let { f -> all.firstOrNull { f(it.name) } }
    }

    /** 객체(또는 그 객체가 구현한 Pleos API 인터페이스)의 함수 목록 */
    fun functionsOf(target: Any): List<KFunction<*>> {
        val classes = mutableListOf<KClass<*>>()
        // 구현 클래스는 이름이 가려져 있을 수 있어서, ai.pleos 패키지의 인터페이스를 먼저 봅니다.
        target.javaClass.interfaces.filter { it.name.startsWith("ai.pleos") }.forEach { classes += it.kotlin }
        classes += target::class
        return classes.flatMap { k -> runCatching { k.memberFunctions.toList() }.getOrDefault(emptyList()) }
    }

    /**
     * 영역(area) 매개변수에 넣을 수 있는 값 이름 목록. 예: DoorArea → [ROW_1_LEFT, ROW_1_RIGHT, ROW_2_LEFT, ...]
     * 영역이 필요 없는 함수면 빈 목록.
     */
    fun areaNames(fn: KFunction<*>): List<String> {
        val areaClass = fn.parameters.firstOrNull { it.isArea() }?.type?.classifier as? KClass<*> ?: return emptyList()
        return enumOfArea(areaClass)?.enumConstants?.map { (it as Enum<*>).name }.orEmpty()
    }

    /**
     * 조회 함수를 부릅니다. 결과는 메인 스레드에서 [onValue] 로 전달됩니다.
     * @param area 영역 이름 (예: "ROW_2_LEFT"). 영역이 필요 없으면 null
     * @return 호출 자체가 됐으면 true
     */
    fun get(target: Any, fn: KFunction<*>, area: String?, onValue: (Any?) -> Unit, onError: (Throwable) -> Unit = {}): Boolean {
        return try {
            val args = mutableMapOf<KParameter, Any?>()
            for (p in fn.parameters) {
                when {
                    p.kind == KParameter.Kind.INSTANCE -> args[p] = target
                    p.isArea() -> args[p] = makeArea(p.type.classifier as KClass<*>, area)
                    p.isFunction() -> {
                        val failure = p.name.orEmpty().contains("fail", ignoreCase = true)
                        args[p] = lambdaFor(p) { values ->
                            main.post {
                                if (failure) onError(values.firstOrNull() as? Throwable ?: RuntimeException("onFailed"))
                                else onValue(values.lastOrNull())
                            }
                        }
                    }
                    p.isOptional -> Unit
                    else -> args[p] = defaultFor(p.type.classifier as? KClass<*>)
                }
            }
            val result = fn.callBy(args)
            // 결과를 콜백이 아니라 바로 돌려주는 함수도 대비
            if (fn.parameters.none { it.isFunction() } && result != null && result != Unit) {
                main.post { onValue(result) }
            }
            true
        } catch (t: Throwable) {
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            Log.w(TAG, "${fn.name}($area) 실패: ${cause.message}")
            onError(cause)
            false
        }
    }

    /** 이름이 [className] 인 리스너 인터페이스를 만들어 줍니다. 어떤 함수가 불리든 [onCall](함수 이름, 인자들) */
    fun listener(className: String, onCall: (String, List<Any?>) -> Unit): Any? {
        val cls = try { Class.forName(className) } catch (_: Throwable) { return null }
        return Proxy.newProxyInstance(cls.classLoader, arrayOf(cls)) { proxy, method, a ->
            when (method.name) {
                "equals" -> proxy === a?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "CarPersonaListener(${cls.simpleName})"
                else -> {
                    val values = a?.toList().orEmpty()
                    main.post { onCall(method.name, values) }
                    if (method.returnType == java.lang.Boolean.TYPE) false else null
                }
            }
        }
    }

    /** 데이터 객체에서 이름에 [keys] 중 하나가 들어간 속성 값을 꺼냅니다. 예: prop(info, "poiName", "name") */
    fun prop(obj: Any?, vararg keys: String): Any? {
        if (obj == null) return null
        for (key in keys) {
            obj.javaClass.methods.firstOrNull {
                it.parameterCount == 0 && (it.name.equals("get$key", true) || it.name.equals(key, true))
            }?.let { m -> return runCatching { m.invoke(obj) }.getOrNull() }
        }
        for (key in keys) {
            obj.javaClass.methods.firstOrNull {
                it.parameterCount == 0 && it.name.startsWith("get") && it.name.contains(key, true)
            }?.let { m -> return runCatching { m.invoke(obj) }.getOrNull() }
        }
        return null
    }

    /** 생성자를 이름 붙은 값으로 부릅니다. 모르는 필수 값은 기본값(0, "", 첫 번째 enum)으로 채움 */
    fun construct(cls: KClass<*>, values: Map<String, Any?>): Any? {
        val ctor = cls.primaryConstructor ?: cls.constructors.maxByOrNull { it.parameters.size } ?: return null
        val args = mutableMapOf<KParameter, Any?>()
        for (p in ctor.parameters) {
            val key = values.keys.firstOrNull { it.equals(p.name, true) }
            when {
                key != null -> args[p] = values[key]
                p.isOptional -> Unit
                p.type.isMarkedNullable -> args[p] = null
                else -> args[p] = defaultFor(p.type.classifier as? KClass<*>)
            }
        }
        return ctor.callBy(args)
    }

    // ───────── 값 해석 (SDK가 Boolean, Int, enum 중 무엇으로 주든 사람이 읽는 뜻으로) ─────────

    private val offWords = setOf("OFF", "NONE", "CLOSE", "CLOSED", "UNKNOWN", "FALSE", "NOT_OCCUPIED", "VACANT", "EMPTY", "STOP", "INACTIVE", "DISABLED", "UNOCCUPIED", "NORMAL", "PARK")

    /** 켜짐/열림/있음이면 true */
    fun isOn(v: Any?): Boolean? = when (v) {
        null -> null
        is Boolean -> v
        is Number -> v.toFloat() != 0f
        is Enum<*> -> v.name.uppercase() !in offWords
        is Collection<*> -> v.any { isOn(it) == true }
        else -> v.toString().uppercase().let { s -> s.isNotBlank() && s !in offWords && !s.startsWith("OFF") }
    }

    fun number(v: Any?): Float? = when (v) {
        is Number -> v.toFloat()
        is Collection<*> -> v.firstNotNullOfOrNull { number(it) }
        else -> v?.toString()?.toFloatOrNull()
    }

    fun text(v: Any?): String = when (v) {
        null -> "null"
        is Enum<*> -> v.name
        is Collection<*> -> v.joinToString(prefix = "[", postfix = "]") { text(it) }
        is Float -> "%.1f".format(v)
        else -> v.toString()
    }

    // ───────── 내부 ─────────

    private fun KParameter.isFunction(): Boolean =
        (type.classifier as? KClass<*>)?.qualifiedName?.startsWith("kotlin.Function") == true

    private fun KParameter.isArea(): Boolean =
        (type.classifier as? KClass<*>)?.qualifiedName?.let {
            it.startsWith("ai.pleos") && it.endsWith("Area")
        } == true

    private fun enumOfArea(areaClass: KClass<*>): Class<*>? =
        areaClass.primaryConstructor?.parameters?.firstOrNull()?.type?.classifier
            ?.let { (it as? KClass<*>)?.java }?.takeIf { it.isEnum }

    private fun makeArea(areaClass: KClass<*>, name: String?): Any? {
        val enumClass = enumOfArea(areaClass) ?: return null
        val constants = enumClass.enumConstants!!.map { it as Enum<*> }
        val value = constants.firstOrNull { it.name == name } ?: constants.first()
        return areaClass.primaryConstructor!!.call(value)
    }

    private fun lambdaFor(p: KParameter, report: (List<Any?>) -> Unit): Any {
        val n = (p.type.classifier as KClass<*>).qualifiedName!!.removePrefix("kotlin.Function").toIntOrNull() ?: 1
        val f0: () -> Unit = { report(emptyList()) }
        val f1: (Any?) -> Unit = { a -> report(listOf(a)) }
        val f2: (Any?, Any?) -> Unit = { a, b -> report(listOf(a, b)) }
        val f3: (Any?, Any?, Any?) -> Unit = { a, b, c -> report(listOf(a, b, c)) }
        return when (n) {
            0 -> f0
            1 -> f1
            2 -> f2
            else -> f3
        }
    }

    private fun defaultFor(k: KClass<*>?): Any? = when (k) {
        Boolean::class -> false
        Int::class -> 0
        Long::class -> 0L
        Float::class -> 0f
        Double::class -> 0.0
        String::class -> ""
        else -> k?.java?.takeIf { it.isEnum }?.enumConstants?.let { c ->
            c.firstOrNull { (it as Enum<*>).name == "RECOMMENDED" } ?: c.firstOrNull()
        }
    }
}
