package com.carpersona.app.ai

import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.BadRequestException
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import com.carpersona.app.persona.Dialogue
import com.carpersona.app.persona.TalkContext
import java.time.Duration

/**
 * Claude API로 대답 만들기 (제안서의 "LLM API").
 *
 * - 공식 Anthropic Java SDK 사용 (Kotlin에서 그대로 씀)
 * - 모델: claude-opus-5-5, 짧은 차 안 대화라 effort LOW
 * - 서버 측 대체(fallbacks: "default"): 안전 분류기가 거절하면 서버가 다른 모델로 다시 시도
 * - 10초 안에 답이 없거나 오류면 규칙 기반 대사(RuleBrain)로 바로 대체 → 시연이 끊기지 않음
 * - API 키는 설정 화면에서 입력 (코드·GitHub에 넣지 않음)
 */
class ClaudeBrain(private val apiKey: String) : Brain {

    companion object {
        const val MODEL = "claude-opus-5-5"
        private const val TAG = "CarPersona/Claude"
    }

    private val client: AnthropicClient by lazy {
        AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofSeconds(10))
            .maxRetries(1)
            .build()
    }

    private var useFallbacks = true

    override fun reply(ctx: TalkContext, question: String, history: List<ChatLine>): Reply {
        return try {
            val text = ask(ctx, question, history)
            if (text.isNullOrBlank()) fallback(ctx, question, "AI 응답 없음")
            else Reply(Dialogue.limit(ctx, text.trim()), byAi = true)
        } catch (e: BadRequestException) {
            // 서버 측 대체 기능을 이 계정에서 못 쓰면 한 번만 빼고 다시 시도
            if (useFallbacks) {
                useFallbacks = false
                Log.w(TAG, "fallbacks 없이 다시 시도: ${e.message}")
                runCatching { ask(ctx, question, history) }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?.let { Reply(Dialogue.limit(ctx, it.trim()), byAi = true) }
                    ?: fallback(ctx, question, "AI 요청 오류")
            } else fallback(ctx, question, "AI 요청 오류")
        } catch (t: Throwable) {
            Log.w(TAG, "Claude 호출 실패: ${t.message}")
            fallback(ctx, question, "AI 연결 실패")
        }
    }

    private fun ask(ctx: TalkContext, question: String, history: List<ChatLine>): String? {
        val b = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(1024L)
            .system(PersonaPrompt.system(ctx) + "\n\n[이 차의 말투 예시]\n" + PersonaPrompt.example(ctx, question))
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
        if (useFallbacks) {
            b.addBeta("server-side-fallback-2026-07-01")
            b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
        // 최근 대화 몇 줄 (사용자 → 차 순서가 번갈아 와야 함)
        val recent = history.takeLast(6).dropWhile { it.fromCar }
        var expectUser = true
        for (line in recent) {
            if (line.fromCar == !expectUser) {
                if (line.fromCar) b.addAssistantMessage(line.text) else b.addUserMessage(line.text)
                expectUser = !expectUser
            }
        }
        if (!expectUser) b.addAssistantMessage("(이어서)")
        b.addUserMessage(question)

        val msg = client.beta().messages().create(b.build())
        if (msg.stopReason().orElse(null) == BetaStopReason.REFUSAL) return null
        return msg.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("").ifBlank { null }
    }

    private fun fallback(ctx: TalkContext, question: String, why: String) =
        Reply(Dialogue.answer(ctx, question), byAi = false, note = "$why → 기본 대사")
}
