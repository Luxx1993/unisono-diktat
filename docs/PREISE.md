# Preise der Spracherkennung (€ pro Stunde Audio)

Die App zeigt den Preis neben jedem Modell in der Modellauswahl (Datei) und in der Auswahl der Echtzeit-Modelle, mit 2 bis 4 Nachkommastellen und ohne überflüssige Nullen. Die Daten stehen in `lib/dictate-core/.../provider/ModelPrices.kt`, die Browser-Version in `js/prices.js` von Unisono.

Quelle: die Preisseiten der Anbieter, abgerufen am 21.10.2026, aktualisiert am 06.11.2026, normale Pay-as-you-go-Preise, Dollarpreise umgerechnet mit 1 € = 1,1215 $ (Gladia, Scaleway und OVHcloud listen in Euro). Aufpreise für Sprechertrennung oder Fachbegriffe sind nicht enthalten. „ca.“ steht bei gerechneten, geschätzten oder nur aus Drittquellen belegten Werten (Token-Preise, Aktionspreise, „ab“-Preise). Wo kein Preis gefunden wurde, zeigt die App keinen.

## Datei

| Anbieter | Modell | €/h |
|---|---|---|
| OpenRouter | openai/whisper-large-v3-turbo | 0,0107 |
| OpenRouter | openai/whisper-large-v3 | 0,0257 |
| OpenRouter | nvidia/parakeet-tdt-0.6b-v3 | 0,0535 |
| OpenRouter | microsoft/mai-transcribe-2 | 0,0892 |
| OpenRouter | openai/gpt-transcribe | 0,2407 |
| OpenRouter | elevenlabs/scribe-v2 | 0,1958 |
| OpenRouter | x-ai/grok-stt-1.0 | 0,0892 |
| OpenRouter | mistralai/voxtral-mini-transcribe | 0,1605 |
| OpenRouter | google/gemini-3.5-transcribe | ca. 0,2675 |
| OpenRouter | deepgram/nova-3 | ca. 0,2311 |
| OpenRouter | assemblyai/universal-3-6-pro | 0,2675 |
| OpenRouter | mistralai/voxtral-mini-transcribe-3-0 | 0,1872 |
| OpenRouter | qwen/qwen3-asr-1.7b | 0,0241 |
| Groq | whisper-large-v3-turbo | 0,0357 |
| Groq | whisper-large-v3 | 0,099 |
| OVHcloud | whisper-large-v3-turbo | 0,046 |
| OVHcloud | whisper-large-v3 | 0,147 |
| Scaleway | whisper-large-v3 | 0,18 |
| Together AI | openai/whisper-large-v3 | 0,0802 |
| Together AI | nvidia/parakeet-tdt-0.6b-v3 | 0,0802 |
| xAI | grok-voice-transcribe-2.0 | ca. 0,0892 |
| Soniox | stt-async-v6 | ca. 0,087 |
| Azure | mai-transcribe-2 | 0,0892 |
| Azure | mai-transcribe-1.5 | 0,321 |
| AssemblyAI | universal-2 | 0,1337 |
| AssemblyAI | universal-3-5-pro | 0,1872 |
| AssemblyAI | universal-3-5-pro-sync | ca. 0,4012 |
| Gladia | solaria-3 | 0,16 |
| Gladia | solaria-1 | 0,27 |
| OpenAI | gpt-4o-mini-transcribe | 0,1605 |
| OpenAI | gpt-transcribe | ca. 0,2407 |
| OpenAI | gpt-4o-transcribe | 0,321 |
| OpenAI | gpt-4o-transcribe-diarize | 0,321 |
| Mistral | voxtral-mini-latest (abgekündigt, aus am 30.04.2027) | ca. 0,1872 |
| Mistral | voxtral-mini-transcribe-3-0 | 0,1872 |
| Mistral | voxtral-small-transcribe-3-0 | 0,214 |
| ElevenLabs | scribe_v2 | 0,1962 |
| Deepgram | nova-3 | 0,23 |
| Gemini | gemini-3.5-transcribe | ca. 0,2675 |
| Gemini | gemini-3.6-flash | ca. 0,2318 |
| Gemini | gemini-3.8-flash | ca. 0,2318 |
| Gemini | gemini-3.5-flash-lite | ca. 0,058 |

## Echtzeit

| Anbieter | Modell | €/h |
|---|---|---|
| Soniox | stt-rt-v6 | 0,107 |
| AssemblyAI | universal-streaming-english | 0,1337 |
| AssemblyAI | universal-streaming-multilingual | 0,1337 |
| AssemblyAI | universal-3-6-pro | 0,4012 |
| xAI | grok-voice-transcribe-2.0 | ca. 0,1783 |
| Deepgram | nova-2 | 0,3121 |
| Deepgram | nova-3 | 0,4102 |
| Deepgram | flux-general-en | 0,4102 |
| Deepgram | flux-general-multi | 0,4191 |
| ElevenLabs | scribe_v2_realtime | 0,3477 |
| Mistral | voxtral-mini-transcribe-realtime-3-0 | 0,428 |
| Mistral | voxtral-mini-transcribe-realtime-2602 | ca. 0,428 |
| Gladia | solaria-1 | 0,68 |

## Weitere Spracherkennungs-Modelle

Neu: OpenAI `whisper-1` (0,321 €/h Datei), `gpt-live-transcribe` und `gpt-realtime-whisper` (0,9095 €/h Echtzeit), Azure `MAI-Transcribe-1` (0,321 €/h), Gemini `gemini-3.5-transcribe-live` (ca. 0,4815 €/h Echtzeit) und Mistral `voxtral-mini-transcribe-realtime-2602` mit 0,321 €/h (die Version 3-0 kostet 0,428 €/h).

## Text-Modelle (Aufräumen, Umformulieren, Zusammenfassen)

Neben jedem Text-Modell steht Eingabe / Ausgabe in € je 1 Million Token („0,09 / 0,45 €/M“). Es sind die normalen Pay-as-you-go-Preise vom 21.10.2026 für kurze Prompts, ohne Batch-, Cache- und Prioritäts-Tarife; Dollarpreise mit 1 € = 1,1215 $ umgerechnet, Scaleway und OVHcloud listen in Euro. Ein Diktat von einer Stunde Text sind grob 10.000 bis 15.000 Token Ausgabe, also meist wenige Cent. „ca.“ steht bei Modell-IDs, die der Anbieter nicht ausdrücklich nennt (aus dem Namen abgeleitet), und bei Werten aus Drittquellen.

Gestaffelt oder befristet:
- **OpenAI** GPT-6/5.6/5.5/5.4: Preise bis 272.000 Token Eingabe, darüber höher (zum Beispiel gpt-6-luna 0,20 / 0,75 $).
- **Anthropic** Haiku 5.5: bis 100.000 Token Prompt 0,10 / 0,50 $, darüber 0,50 / 2,50 $.
- **Gemini** 3.6 und 3.8 Flash: Einführungspreis bis 31.12.2026, ab 1.1.2027 1,50 / 7,50 $. Gemini 4 Argon: bis 30.11.2026 2 / 10 $, danach 4 / 20 $.
- **DeepSeek:** Spitzenzeit-Preis (Mo bis Fr 01 bis 04 und 06 bis 10 Uhr UTC); außerhalb halb so teuer.
- **xAI:** ab 200.000 Token Prompt der doppelte Preis.
- **Cerebras:** gpt-oss-120b und qwen-3.8-27b stehen seit November 2026 auf der Preisseite.
- **OpenRouter:** Anbieterpreise ohne Aufschlag; dazu kommen 5,5 % Gebühr beim Aufladen. Die Anzeige hängt deshalb „+5,5 %“ hinter jeden OpenRouter-Preis, bei Spracherkennung und bei Text-Modellen. Die Auswahl sind die meistgenutzten Modelle vom 20.10.2026.
- **OVHcloud:** Mistral-Small-3.2 ist seit dem 04.11.2026 abgeschaltet; die App stellt gespeicherte Auswahl auf gpt-oss-20b um.

Ohne Preis: Groq llama-Modelle (nur auf Anfrage), SiliconFlow (die Preisseite nennt keine Währung und keine IDs), Together AI (die Preisseite nennt keine IDs; nur gpt-oss-120b ist sicher zugeordnet). Ollama und eigene Server sind kostenlos.

| Anbieter | Modell | Eingabe / Ausgabe in € je 1 Mio. Token |
|---|---|---|
| Anthropic | claude-fable-5 | ca. 8,9166 / 44,5831 |
| Anthropic | claude-fable-5-1 | 8,9166 / 44,5831 |
| Anthropic | claude-haiku-4-5 | ca. 0,8917 / 4,4583 |
| Anthropic | claude-haiku-5-5 | 0,0892 / 0,4458 |
| Anthropic | claude-opus-4-5 | ca. 4,4583 / 22,2916 |
| Anthropic | claude-opus-4-6 | ca. 4,4583 / 22,2916 |
| Anthropic | claude-opus-4-7 | ca. 4,4583 / 22,2916 |
| Anthropic | claude-opus-4-8 | ca. 4,4583 / 22,2916 |
| Anthropic | claude-opus-5 | ca. 4,4583 / 22,2916 |
| Anthropic | claude-opus-5-5 | 3,5667 / 17,8333 |
| Anthropic | claude-sonnet-4-5 | ca. 2,675 / 13,3749 |
| Anthropic | claude-sonnet-4-6 | ca. 2,675 / 13,3749 |
| Anthropic | claude-sonnet-5 | ca. 1,7833 / 8,9166 |
| Anthropic | claude-sonnet-5-5 | 1,7833 / 8,9166 |
| Cerebras | gpt-oss-120b | 0,3121 / 0,6687 |
| Cerebras | qwen-3.8-27b | 0,8827 / 1,3286 |
| DeepInfra | anthropic/claude-haiku-4-5 | 0,8917 / 4,4583 |
| DeepInfra | anthropic/claude-sonnet-5 | 2,675 / 13,3749 |
| DeepInfra | deepseek-ai/deepseek-r1-0528 | 0,4458 / 1,9171 |
| DeepInfra | deepseek-ai/deepseek-v3.2 | 0,2318 / 0,3388 |
| DeepInfra | deepseek-ai/deepseek-v4-flash | 0,0802 / 0,1605 |
| DeepInfra | deepseek-ai/deepseek-v4-flash-0731 | 0,0535 / 0,1605 |
| DeepInfra | deepseek-ai/deepseek-v4-pro | 1,1592 / 2,3183 |
| DeepInfra | google/gemini-2.5-flash | 0,2675 / 2,2292 |
| DeepInfra | google/gemma-4-31b-it | 0,1783 / 0,3567 |
| DeepInfra | meta-llama/llama-3.3-70b-instruct-turbo | 0,0892 / 0,2853 |
| DeepInfra | meta-llama/llama-4-scout-17b-16e-instruct | 0,0892 / 0,2675 |
| DeepInfra | meta-llama/meta-llama-3.1-8b-instruct-turbo | 0,0178 / 0,0357 |
| DeepInfra | microsoft/phi-4 | 0,0624 / 0,1248 |
| DeepInfra | mistralai/mistral-nemo-instruct-2407 | 0,0259 / 0,0267 |
| DeepInfra | mistralai/mistral-small-3.2-24b-instruct-2506 | 0,0669 / 0,1783 |
| DeepInfra | moonshotai/kimi-k2.6 | 0,6687 / 3,1208 |
| DeepInfra | moonshotai/kimi-k3 | 2,5412 / 12,7062 |
| DeepInfra | qwen/qwen3-235b-a22b-instruct-2507 | 0,0802 / 0,4904 |
| DeepInfra | qwen/qwen3-32b | 0,0713 / 0,2497 |
| DeepInfra | qwen/qwen3.5-397b-a17b | 0,4012 / 2,675 |
| DeepInfra | qwen/qwen3.6-27b | 0,2853 / 2,8533 |
| DeepInfra | qwen/qwen3.5-9b | 0,0892 / 0,1337 |
| DeepInfra | openai/gpt-oss-120b | 0,033 / 0,1516 |
| DeepSeek | deepseek-flash | 0,2675 / 1,07 |
| DeepSeek | deepseek-v4-pro | 1,177 / 3,531 |
| Google Gemini | gemini-2.5-flash | 0,2675 / 2,2292 |
| Google Gemini | gemini-2.5-flash-lite | 0,0892 / 0,3567 |
| Google Gemini | gemini-2.5-pro | 1,1146 / 8,9166 |
| Google Gemini | gemini-3-flash-preview | 0,4458 / 2,675 |
| Google Gemini | gemini-3.1-flash-lite | 0,2229 / 1,3375 |
| Google Gemini | gemini-3.1-pro-preview | 1,7833 / 10,70 |
| Google Gemini | gemini-3.5-flash-lite | 0,2675 / 2,2292 |
| Google Gemini | gemini-3.6-flash | 0,6687 / 3,3437 |
| Google Gemini | gemini-3.8-flash | 0,6687 / 3,3437 |
| Google Gemini | gemini-4-argon | 1,7833 / 8,9166 |
| Groq | openai/gpt-oss-120b | 0,1337 / 0,535 |
| Groq | openai/gpt-oss-20b | 0,0669 / 0,2675 |
| Groq | openai/gpt-oss-safeguard-20b | 0,0669 / 0,2675 |
| Groq | qwen/qwen3.8-27b | 0,7133 / 3,5667 |
| Mistral | codestral | ca. 0,2675 / 0,8025 |
| Mistral | ministral-14b-2512 | 0,1783 / 0,1783 |
| Mistral | ministral-3b-2512 | 0,0892 / 0,0892 |
| Mistral | ministral-8b-2512 | 0,1337 / 0,1337 |
| Mistral | mistral-large-2512 (abgekündigt) | ca. 0,4458 / 1,3375 |
| Mistral | mistral-large-4 (Vorschau) | 0,6242 / 1,8725 |
| Mistral | mistral-medium-3-5 | 1,3375 / 6,6875 |
| Mistral | mistral-small-2603 | 0,1337 / 0,535 |
| OpenAI | chat-latest | 4,4583 / 26,7499 |
| OpenAI | gpt-3.5-turbo | 0,4458 / 1,3375 |
| OpenAI | gpt-4-turbo-2024-04-09 | 8,9166 / 26,7499 |
| OpenAI | gpt-4.1 | 1,7833 / 7,1333 |
| OpenAI | gpt-4.1-mini | 0,3567 / 1,4267 |
| OpenAI | gpt-4.1-nano | 0,0892 / 0,3567 |
| OpenAI | gpt-4o | 2,2292 / 8,9166 |
| OpenAI | gpt-4o-mini | 0,1337 / 0,535 |
| OpenAI | gpt-5 | 1,1146 / 8,9166 |
| OpenAI | gpt-5-mini | 0,2229 / 1,7833 |
| OpenAI | gpt-5-nano | 0,0446 / 0,3567 |
| OpenAI | gpt-5-pro | 13,3749 / 106,9996 |
| OpenAI | gpt-5.1 | 1,1146 / 8,9166 |
| OpenAI | gpt-5.2 | 1,5604 / 12,4833 |
| OpenAI | gpt-5.2-pro | 18,7249 / 149,7994 |
| OpenAI | gpt-5.4 | 2,2292 / 13,3749 |
| OpenAI | gpt-5.4-mini | 0,6687 / 4,0125 |
| OpenAI | gpt-5.4-nano | 0,1783 / 1,1146 |
| OpenAI | gpt-5.4-pro | 26,7499 / 160,4993 |
| OpenAI | gpt-5.5 | 4,4583 / 26,7499 |
| OpenAI | gpt-5.5-pro | 26,7499 / 160,4993 |
| OpenAI | gpt-5.6-luna | 0,1783 / 1,07 |
| OpenAI | gpt-5.6-sol | 3,5667 / 17,8333 |
| OpenAI | gpt-5.6-terra | 1,7833 / 10,70 |
| OpenAI | gpt-6-astra | 8,9166 / 44,5831 |
| OpenAI | gpt-6-luna | 0,0892 / 0,4458 |
| OpenAI | gpt-6-sol | 1,7833 / 8,9166 |
| OpenAI | gpt-6.1-sol | 1,7833 / 8,9166 |
| OpenAI | gpt-6.1-terra | 0,8917 / 4,4583 |
| OpenAI | o1 | 13,3749 / 53,4998 |
| OpenAI | o1-pro | 133,7494 / 534,9978 |
| OpenAI | o3 | 1,7833 / 7,1333 |
| OpenAI | o3-mini | 0,9808 / 3,9233 |
| OpenAI | o3-pro | 17,8333 / 71,333 |
| OpenAI | o4-mini | 0,9808 / 3,9233 |
| OpenRouter | anthropic/claude-haiku-5.5 | 0,0892 / 0,4458 |
| OpenRouter | anthropic/claude-opus-5.5 | 3,5667 / 17,8333 |
| OpenRouter | anthropic/claude-sonnet-5.5 | 1,7833 / 8,9166 |
| OpenRouter | deepseek/deepseek-v4-flash | 0,0811 / 0,1623 |
| OpenRouter | deepseek/deepseek-v4-flash-0731 | 0,3923 / 1,177 |
| OpenRouter | deepseek/deepseek-v4.1-flash | 0,0499 / 0,107 |
| OpenRouter | deepseek/deepseek-v4.1-flash-1017 | 0,2675 / 1,07 |
| OpenRouter | meta/muse-spark-1.3-contributor | 0,0892 / 0,1783 |
| OpenRouter | minimax/minimax-m3.1-flash:free | 0 / 0 |
| OpenRouter | nvidia/nemotron-3-ultra-550b-a55b:free | 0 / 0 |
| OpenRouter | openai/gpt-6-luna | 0,0892 / 0,4458 |
| OpenRouter | stepfun/step-5-preview | 0,8917 / 2,4075 |
| OpenRouter | tencent/hy3 | 0,1177 / 0,4708 |
| OpenRouter | tencent/hy4-preview | 0,7436 / 2,23 |
| OpenRouter | xiaomi/mimo-v2.6-flash | 0,1248 / 0,2497 |
| OpenRouter | z-ai/glm-5.3 | 0,0704 / 1,2483 |
| OpenRouter | z-ai/glm-5.3-flash | 0,0419 / 0,107 |
| OVHcloud | gpt-oss-120b | 0,08 / 0,40 |
| OVHcloud | gpt-oss-20b | 0,04 / 0,15 |
| OVHcloud | meta-llama-3_3-70b-instruct | 0,67 / 0,67 |
| OVHcloud | mistral-nemo-instruct-2407 | 0,13 / 0,13 |
| OVHcloud | qwen2.5-vl-72b-instruct | 0,91 / 0,91 |
| OVHcloud | qwen3.5-397b-a17b | 0,60 / 3,60 |
| OVHcloud | qwen3.5-9b | 0,10 / 0,15 |
| OVHcloud | qwen3.6-27b | 0,40 / 2,70 |
| OVHcloud | qwen3.8-27b | 0,40 / 2,70 |
| Scaleway | deepseek-v4-flash-0731 | 0,40 / 0,80 |
| Scaleway | deepseek-v4.1-flash | 0,60 / 1,80 |
| Scaleway | gemma-4-26b-a4b-it | 0,25 / 0,50 |
| Scaleway | glm-5.2 | 1,80 / 5,50 |
| Scaleway | glm-5.3 | 1,80 / 5,50 |
| Scaleway | gpt-oss-120b | 0,15 / 0,60 |
| Scaleway | llama-3.3-70b-instruct | 0,90 / 0,90 |
| Scaleway | mistral-medium-3.5-128b | 1,50 / 7,50 |
| Scaleway | mistral-small-3.2-24b-instruct-2506 | 0,15 / 0,35 |
| Scaleway | qwen3-235b-a22b-instruct-2507 | 0,75 / 2,25 |
| Scaleway | qwen3.5-397b-a17b | 0,60 / 3,60 |
| Scaleway | qwen3.6-35b-a3b | 0,25 / 1,50 |
| Scaleway | qwen3.8-27b | 0,60 / 3,30 |
| Together AI | openai/gpt-oss-120b | 0,1337 / 0,535 |
| xAI | grok-4.20-0309-non-reasoning | 1,1146 / 2,2292 |
| xAI | grok-4.20-0309-reasoning | 1,1146 / 2,2292 |
| xAI | grok-4.20-multi-agent-0309 | 1,1146 / 2,2292 |
| xAI | grok-4.3 | 1,1146 / 2,2292 |
| xAI | grok-4.5 | 1,7833 / 5,35 |
| xAI | grok-4.6 | 1,7833 / 5,35 |
| xAI | grok-4.7 | 1,7833 / 5,35 |
| xAI | grok-build-0.1 | 0,8917 / 1,7833 |

## Nicht gefunden

AssemblyAI Universal-3 Pro, SiliconFlow (SenseVoice wird nach Zeichen abgerechnet), Deepgram Nova-2 und Flux für Dateien, die Preise des Aufräumens mit KI-Modellen (Token-Preise, nicht recherchiert). Groq rechnet mindestens 10 s je Anfrage ab, Scaleway gibt 60 Minuten frei, OpenRouter erhebt 5,5 % beim Aufladen; Deepgram Nova-3 und Flux Streaming sind derzeit Aktionspreise.
