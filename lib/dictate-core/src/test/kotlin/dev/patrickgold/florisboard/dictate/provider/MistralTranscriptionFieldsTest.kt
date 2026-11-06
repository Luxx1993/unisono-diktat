package dev.patrickgold.florisboard.dictate.provider

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Mistral's own STT endpoint gets its narrower field set; Voxtral elsewhere keeps the OpenAI form. */
class MistralTranscriptionFieldsTest : FunSpec({
    test("only Voxtral on api.mistral.ai counts as Mistral's own endpoint") {
        isMistralVoxtral("https://api.mistral.ai/v1/", "voxtral-mini-transcribe-3-0") shouldBe true
        isMistralVoxtral("https://api.mistral.ai/v1/", "Voxtral-Mini-Latest") shouldBe true
        isMistralVoxtral("https://openrouter.ai/api/v1/", "mistralai/voxtral-mini-transcribe-3-0") shouldBe false
        isMistralVoxtral("http://localhost:8000/v1/", "voxtral-mini-transcribe-3-0") shouldBe false
        isMistralVoxtral("https://api.mistral.ai/v1/", "mistral-small-2603") shouldBe false
    }
})
