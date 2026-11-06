package com.carpersona.app.ai

import com.carpersona.app.persona.Dialogue
import com.carpersona.app.persona.TalkContext

/** 대답 한 줄 */
data class Reply(val text: String, val byAi: Boolean, val note: String? = null)

/** 대화 한 줄 (화면 표시 + AI에게 이전 대화로 전달) */
data class ChatLine(val fromCar: Boolean, val text: String, val byAi: Boolean = false)

/**
 * 대답을 만드는 "두뇌". 교체 가능한 모듈입니다.
 *  - RuleBrain   : 규칙 기반 대사 (키·인터넷 없이 항상 동작, 시연 안정성)
 *  - ClaudeBrain : Claude API (설정에서 API 키를 넣었을 때). 실패하면 RuleBrain 으로 자동 대체
 */
interface Brain {
    /** 백그라운드 스레드에서 불립니다 */
    fun reply(ctx: TalkContext, question: String, history: List<ChatLine>): Reply
}

object RuleBrain : Brain {
    override fun reply(ctx: TalkContext, question: String, history: List<ChatLine>) =
        Reply(Dialogue.answer(ctx, question), byAi = false)
}
