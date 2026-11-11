# TTS·STT 확인 기록 (F-01)

확인 방법: 공식 문서 + Pleos Maven의 `TextToSpeech-3.3.1.aar`, `SpeechToText-3.3.1.aar`를 `javap`로 읽고, Pleos Connect 에뮬레이터 2.1.0에서 실행(2026-11-05).
표기: [확인] AAR·에뮬레이터에서 확인 / [실측필요] 사람이 확인해야 함

## 결론 (F-01)
1. **마이크:** 에뮬레이터에서 앱이 PC 마이크를 받으려면 Extended Controls > Microphone > **Enable Host Microphone Access**를 켜야 한다(기본 꺼짐). 꺼져 있으면 STT는 `NO_RESPONSE`(앱 자체 코드), 녹음은 무음이다. 에뮬레이터 재시작 후 유지되는지는 [실측필요].
2. **ON_DEVICE 한국어 STT는 동작한다.** 호스트 마이크를 켜고 말하자 `STT end code=OK heard="진짜 녹음이 돼야 되는데"`처럼 한국어 문장이 그대로 인식됐다. TTS(ko-KR)도 동작한다. 인식 정확도는 [실측필요].
3. **request→ready 지연:** 보통 0.1~0.6초(앱 시작 때 initialize 해 둔 상태, 연속 5회·TTS 직후 간격 실험 모두 최대 1.4초). 단, **앱 시작 직후 첫 차례(인사 TTS → 듣기)에서만 최대 5.6초**가 관측됐다. 원인은 미확인이며 F-15(PT 안정화)에서 다시 확인한다. 상세 측정은 아래.
4. 미해결: 기준 6(뒷좌석 마이크·출력 존)은 문서에 없어 실제 차량에서 사람이 확인해야 한다.

## 의존성·권한 [확인]
- `ai.pleos.playground:TextToSpeech:3.3.1`, `ai.pleos.playground:SpeechToText:3.3.1` (같은 Maven, 인증 없음). 둘 다 `<queries>`를 AAR이 자동 병합한다.
- manifest 권한: `pleos.car.permission.TTS_SERVICE`, `pleos.car.permission.STT_SERVICE` (에뮬레이터에서 granted=true). 점검용 녹음에는 `android.permission.RECORD_AUDIO`(런타임 요청 필요).

## 사용 형태 [확인]
```kotlin
val tts = TextToSpeech(context, Mode.ON_DEVICE)      // ai.pleos.playground.tts
tts.initialize(OnInitializeListener)                  // 먼저! 
tts.addEventListener(EventListener)                   // initialize 뒤. 앞에 부르면 앱이 죽는다(lateinit tts 미초기화)
tts.setLocale(Locale.KOREA)                           // 초기화 성공 콜백 안에서
tts.speak(text, true); tts.stop()

val stt = SpeechToText(context, Mode.ON_DEVICE)       // ai.pleos.playground.stt
stt.initialize(OnInitializeListener); stt.addListener(ResultListener); stt.setLocale(Locale.KOREA)
stt.request(); stt.stop()
```
- 두 SDK 모두 `initialize` → 리스너 등록 순서를 지킨다. 문서의 순서와 같다.
- TTS 콜백: onStart, onDone, onStop, onError(ErrorType), onReady, onUpdatedRms. STT 콜백: onReady, onStartedRecognition, onUpdated(text, completed), onEndedRecognition, onError(ErrorType).
- ErrorType 12종(TTS·STT 동일 이름): AUDIO_ERROR, MIC_ERROR, SERVER_INTERNAL_ERROR, NETWORK_ERROR, INVALID_REQUEST_ERROR, TIMEOUT, UNAUTHENTICATED, PERMISSION_DENIED, ON_DEVICE_ENGINE_ERROR, UNKNOWN, UNAVAILABLE_LANGUAGE, NOT_SUPPORTED_API. 숫자 코드는 없고 이름으로 로그를 남긴다.

## 에뮬레이터 실행 결과 (ON_DEVICE)
| 항목 | 결과 |
|---|---|
| TTS ON_DEVICE, ko-KR | [확인] `TTS start` → `TTS done`. 초기화 서비스 연결에 약 2초, 발화 시작까지 추가 지연 있음 |
| STT ON_DEVICE | [확인] 엔진 기동, `request()` 뒤 약 3.6초에 `onReady`, 이후 청취 진행. 마이크 접근이 꺼져 있을 때는 결과 없음(아래 "호스트 마이크 켠 실측" 참고) → 앱 타임아웃(30초) `STT end code=NO_RESPONSE` |
| `onStartedRecognition` | 무음에서는 호출되지 않았다. 그래서 `STT start` 로그는 `request()` 직전에 앱이 남긴다 |
| 실제 말했을 때 인식 결과·지연 | [확인 2026-11-05] 호스트 마이크 접근을 켜고 말하자 `STT end code=OK heard="진짜 녹음이 돼야 되는데"`(한국어 문장 그대로 인식). 지연은 아래 "호스트 마이크 켠 실측" |
| MediaRecorder 3초 녹음·재생 | [확인] 호스트 마이크를 켠 뒤 `REC amp max=4722`(무음이면 0)로 소리가 들어왔다. 녹음 파일은 `build/rec-test.m4a`로 꺼냈고 사람이 청취해 확인할 것. 코드 동작: `REC saved ms=3201 bytes=7722`, `REC played`. **bytes는 무음 판정 근거가 아니다**(3초 고정 길이·고정 비트레이트라 소리와 무관하게 비슷). 판정은 `REC amp max=`(100ms마다 읽은 `getMaxAmplitude` 최댓값, 0=무음)와 사람이 직접 들어 보는 것으로 한다. |
| ON_DEVICE 불가 시 대안 | 해당 없음(동작함). 필요해지면 HYBRID(앱 등록 필요) → 안드로이드 기본 TTS/STT 순서로 사람에게 결정을 넘긴다 |

## 호스트 마이크 켠 실측 (2026-11-05)
**원인과 해결: Extended Controls > Microphone > "Enable Host Microphone Access"가 기본 꺼짐이었다. 이것을 켜야 에뮬레이터 앱이 PC 마이크 소리를 받는다.** (자동차 이미지에서도 이 메뉴가 있었다. 앞서 `-allow-host-audio`·이어폰 재시작만으로는 해결되지 않았다.)
- 에뮬레이터 재시작 없이 켠 직후 점검: `STT start` 09.098 → `STT ready` 14.689(요청 후 5.6초, 엔진 기동) → `STT speech-begin` 15.384(말 시작 감지, 이후 5회 반복 호출) → `STT end code=OK heard="진짜 녹음이 돼야 되는데"` 17.472.
- 지연: `STT start`~`STT end` 8.4초. 이 중 엔진 준비(`ready`)까지가 5.6초, 말 시작 감지부터 최종 결과까지 약 2.1초. 첫 요청의 엔진 기동이 느릴 수 있어 F-02에서는 게임 시작 전에 미리 한 번 `request`로 데우는 방안을 검토할 가치가 있다(결정은 F-02에서).
- `onStartedRecognition`(`STT speech-begin`)은 말소리가 있으면 호출되고, 한 번의 발화에 여러 번 올 수 있다.
- 사람이 말한 문장은 "빨간 사과"였으나 인식 문장이 다른 것은 말한 내용이 달랐기 때문인지 인식 오류인지 로그로는 알 수 없다. 인식 정확도 평가는 별도 [실측필요].
- **[실측필요]** 에뮬레이터를 재시작한 뒤에도 이 설정이 유지되는지(이번에는 재시작하지 않고 켠 채로 검증).
- **[실측필요]** 녹음 파일(`build/rec-test.m4a`)을 사람이 들어 보고 소리가 맞는지.

## 호스트 마이크 시도 기록 (2026-11-05) — 위 설정을 켜기 전
Windows 마이크 접근 허용 → `emulator -allow-host-audio`로 재시작(시작 로그 `Allowing host microphone input`) → 이어폰 마이크로 재시작, 총 3회 모두 `STT end code=NO_RESPONSE`. 다만 이 3회의 녹음은 bytes만 봤고 진폭(`amp max`)은 재지 않았다 → 이 시도들의 실패 원인은 위 설정이 꺼져 있었던 것으로 확인됐다.

## STT 준비 지연(request→ready) 측정 (2026-11-05, 호스트 마이크 켠 에뮬레이터)
구현 방식은 정하지 않고 측정만 했다. `ready` = `STT start`(request 직전) → `STT ready`(SDK onReady)까지의 시간. 임시 측정 코드는 커밋하지 않았다(말하지 않고 request 후 12초 뒤 stop, 2초 쉬고 반복).

**1. 연속 5회 (앱 시작 때 initialize 해 둔 상태, 6초 뒤 시작)**
| 회차 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|
| ready | 0.14초 | 0.25초 | 0.38초 | 0.33초 | 0.29초 |
→ 첫 회만 느리지 않다. 5회 모두 0.4초 이하(평균 약 0.28초).

**2. initialize를 미리 한 경우 vs 요청 직전에 한 경우** (lazy는 새 프로세스 3번, 회차별 request→ready)
| | initialize 소요 | 1회차 ready | 2회차 | 3회차 | 4회차 |
|---|---|---|---|---|---|
| 미리 함(위 1번) | 앱 시작 시 0.02초 | 0.14초 | 0.25초 | 0.38초 | 0.33초 |
| lazy 1번째 프로세스 | 0.42초 | 0.30초 | 1.09초 | 0.29초 | 0.17초 |
| lazy 2번째 | 0.45초 | 0.25초 | 0.12초 | 0.13초 | 0.14초 |
| lazy 3번째 | 0.36초 | 0.25초 | 0.12초 | 0.61초 | 0.13초 |
→ initialize를 요청 직전에 하면 약 0.4초가 더 든다. 그 뒤 ready는 미리 한 경우와 비슷하게 0.1~0.6초(최대 1.09초 1회).

**해석(주의)**
- 위 측정에서는 3.6~5.6초 지연이 재현되지 않았다. 앞서 `VoiceCheck`(인사 TTS 직후 듣기)에서 같은 지표를 재면 5회가 3.6 / 0.15 / 2.4 / 4.3 / 5.6초로 크게 흔들렸다.
- 두 측정의 차이는 "request 직전에 TTS가 막 끝났는가"다. TTS 직후에 지연이 커질 가능성이 있지만 **이번에는 검증하지 않았다**(가설). 같은 조건에서 TTS 직후 request를 반복 측정하는 통제 실험이 필요하다.
- 따라서 "미리 initialize"는 약 0.4초를 줄일 뿐이고, 큰 지연의 원인은 아직 불명이다. 구현 방식은 이 결과를 보고 정한다.
- TTS 재생 중 request를 거는 방식은 반향(앱 목소리를 아이 답으로 인식) 위험이 있어 시도하지 않았다. 필요하면 테스트를 먼저 설계해 보여 준다.

## TTS 직후 request 지연 통제 실험 (2026-11-05)
가설: "TTS가 막 끝난 직후 request()를 걸면 ready가 늦어진다." 구현 방식은 정하지 않고 측정만 했다. 임시 측정 코드는 커밋하지 않았다. 인사 TTS("안녕, 같이 놀자!")를 말하고 `TTS done` 콜백 뒤 간격을 두고 request, 말은 하지 않고 request→ready만 쟀다(조건별 5회 연속, 같은 프로세스).

| 간격 | 1회 | 2회 | 3회 | 4회 | 5회 | 평균 | 중앙값 | 최대 |
|---|---|---|---|---|---|---|---|---|
| A: 0ms | 0.27 | 1.42 | 0.22 | 0.18 | 0.91 | 0.60 | 0.27 | 1.42 |
| B: 300ms | 0.36 | 0.80 | 0.61 | 0.60 | 0.61 | 0.60 | 0.61 | 0.80 |
| C: 1000ms | 0.56 | 0.13 | 0.17 | 0.59 | 0.24 | 0.34 | 0.24 | 0.59 |
(단위 초, request→ready)

**함께 본 것**
1. `TTS done` 콜백과 실제 오디오가 끝나는 시각 차이: `AudioManager.isMusicActive`가 `TTS done` 뒤에도 약 0.19~0.23초(15회 모두 189~234ms) 더 true였다. 반대로 `AudioPlaybackCallback`의 "활성 재생 0개" 이벤트는 `TTS done`과 거의 같은 시각(−5~+23ms)에 왔다. 즉 플레이어 정지 보고는 done과 동시이고, 스트림 활성 표시가 약 0.2초 더 남는다. 어느 쪽이 "실제 소리가 끝난 시각"인지는 이 측정만으로 단정할 수 없다.
2. 오디오 포커스 변화 이벤트: 시스템 로그(`AudioFocus`, `MediaFocusControl`)에서 **관측하지 못했다**(0줄). 이벤트가 없었다는 증거가 아니라 기본 로그 레벨에 안 찍힌 것일 수 있다. 앱에서 포커스를 직접 요청하면 조건이 바뀌므로 하지 않았다.

**결론(한 줄)**: 어느 간격(0·300·1000ms)에서도 앞서 본 3.6~5.6초 지연은 재현되지 않았고(전 조건 최대 1.42초) 간격이 길수록 약간 짧아 보이나 조건당 5회라 우연과 구분되지 않으므로, 이번 측정 범위에서는 "지연이 사라지는 조건"을 특정하지 못했다.
- 앞선 큰 지연의 원인은 여전히 불명이다. 이번과 달랐던 점: 앱 실행 직후 첫 `VoiceCheck`(프로세스 첫 TTS, 가끔 TTS 자체가 2.6~8.5초), 실제 말소리/마이크 상태. 이 중 어느 것이 원인인지는 검증하지 않았다.

## STT ErrorType 의미 (공식 문서 Error Codes)
출처: document.pleos.ai …/SpeechToText/error-code (요약 도구로 가져와 문구는 원문에 가깝게 옮김).
| ErrorType | 문서상 설명 |
|---|---|
| AUDIO_ERROR | 입력 오디오 데이터 처리 실패 |
| MIC_ERROR | 마이크 입력을 시작하거나 처리하는 중 오류 |
| TIMEOUT | 제한 시간 안에 요청이 완료되지 않음 (시간값은 문서에 없음) |
| SERVER_INTERNAL_ERROR | Speech 서비스 내부 오류 |
| NETWORK_ERROR | 네트워크 통신 실패 |
| INVALID_REQUEST_ERROR | 요청 값 또는 요청 상태가 올바르지 않음 (호출 순서·중복 요청 확인) |
| UNAUTHENTICATED | 앱 인증 실패 |
| PERMISSION_DENIED | 앱에 STT 서비스 접근 권한 없음 (`STT_SERVICE` 권한 확인) |
| ON_DEVICE_ENGINE_ERROR | 온디바이스 음성 인식 엔진 오류 |
| UNKNOWN | 분류되지 않은 오류 |
| UNAVAILABLE_LANGUAGE | 요청한 언어를 쓸 수 없음 |
| NOT_SUPPORTED_API | 현재 모드·실행 환경에서 지원하지 않는 API |
- **`NO_RESPONSE`는 SDK 값이 아니다.** 문서에 없고, 우리 앱(`pleos/sdk/PleosStt`)이 30초 안에 SDK 콜백(결과·오류)이 하나도 오지 않을 때 스스로 붙이는 코드다. 의미는 "SDK가 결과도 오류도 주지 않았다"뿐이며, 무음·마이크 불통·엔진 대기 중 어느 쪽인지는 이 값만으로 알 수 없다.
- 문서에 **없는** 것: 무음일 때 어떤 콜백이 오는지, ON_DEVICE 전용 동작, TIMEOUT·MIC_ERROR·AUDIO_ERROR가 "무음/입력 없음"을 뜻하는지. 실제 환경 로그로만 확인할 수 있다.
- 에뮬레이터 실측: 무음으로 보이는 상황에서 `onReady`는 왔지만 `onStartedRecognition`·`onError`는 오지 않았다.

## 주의
- 앱은 사용자 10으로 실행된다. 에뮬레이터에서 권한을 미리 줄 때는 `adb shell pm grant --user 10 ...`.
- TTS 시작이 느릴 수 있어(서비스 연결 대기) 첫 발화 지연은 F-02 시연 전에 다시 잰다.

## 마이크·스피커 위치 [실측필요 / 문서에 없음]
- 공식 문서에서 뒷좌석 마이크 선택이나 출력 존 지정 설명을 찾지 못했다.
- TextToSpeech AAR에는 `setAudioZoneId(Int)`, `setAudioAttributes(AudioAttributes)`가 있다 [확인: javap]. 쓰임·유효한 존 번호는 미확인이라 F-01에서는 호출하지 않았다. 실제 차량에서 뒷좌석 스피커로 보낼 수 있는지 사람이 확인해야 한다.
- STT는 존 지정 API가 보이지 않는다(`sendAudio(ByteArray)`로 직접 오디오를 넣는 경로는 있음).

## 앱 발화 중 듣기와 요청 순서 (F-23, 2026-11-11)
확인 방법: 공식 문서(doc-scout) + Pleos Connect 에뮬레이터에서 임시 측정 코드(커밋하지 않음)로 TTS 재생 0.8초 뒤 STT `request()`.
| 항목 | 결과 |
|---|---|
| TTS 재생 중 `request()` | [확인] 허용된다. `STT ready`가 request 뒤 0.07초에 왔고, TTS는 끊기지 않고 `TTS done`까지 재생됐다. 오류 콜백 없음 |
| TTS 중 반향(앱 목소리가 인식됨) | [실측필요] 이번 측정은 결과 없이 30초 뒤 `NO_RESPONSE`. 호스트 마이크 접근 설정을 확인하지 않아 "반향 없음"의 근거가 아니다. 실제 마이크로 재생 중 "그만할래"가 인식되는지, 앱 음성이 응답으로 접수되지 않는지 사람이 확인한다 |
| 문서 | barge-in·반향 제거·동시 입출력에 대한 설명 없음(SpeechToText result-listeners, start-and-stop) |
| 인식 후보 | `onUpdated(stt, completed)` 하나뿐. 후보 여러 개를 주는 API는 문서에 없다. 앱은 `completed=true`인 문장 하나를 후보 하나로 넘긴다 |
| TTS `stop()` | 문서: `onStop()`이 오고 `onDone()`은 정상 완료에만 온다. 앱은 onStop을 `SpeakResult.Cancelled`(로그 `TTS cancel`)로 구분한다 |
| STT `stop()` 뒤 다음 `request()` | 문서: stop()은 마이크를 놓고 `onEndedRecognition`이 온다, 순서 위반은 `INVALID_REQUEST_ERROR`. 에뮬레이터: 앱 타임아웃(NO_RESPONSE) 뒤 stop()했을 때 1초 안에 `onEndedRecognition`이 오지 않았다 |

**앱이 정한 순서(PleosTts·PleosStt):** 말하기·듣기는 각각 한 번에 하나만 실행한다. 요청이 취소·시간 초과로 끝나면 stop()을 부르고 끝 콜백(TTS: done·stop·error, STT: onEndedRecognition·onError)을 최대 1초 기다린 뒤 다음 요청을 낸다. 콜백에 요청 ID가 없으므로 이전 요청의 늦은 콜백이 새 요청의 결과가 되지 않게 하기 위해서다. 말하기와 듣기는 서로 막지 않는다(앱 발화 중 제어 요청용).
**반향 대책(앱 쪽, F-21에서 적용):** 앱이 말하는 동안 들은 문장은 제어 요청(재안내·휴식·재개·종료)만 처리하고, 지금 말하는 문장과 겹치면 버린다.
**못 알아들음 기준(F-01-03):** STT 오류, 빈 결과(최종 결과 없이 끝남, 로그 `STT end code=EMPTY`), 앱 타임아웃 `NO_RESPONSE`는 모두 UNHEARD. 신호 뒤 14초 동안 결과가 없으면 SILENT. 짧은 결과 기준은 가족 테스트 후 정한다.
**ON_DEVICE 불가 시:** 서버 모드(HYBRID)로 자동 전환하지 않는다. 코드는 `Mode.ON_DEVICE`만 쓴다.

## 오디오 포커스 (F-23)
- Pleos 가이드가 명시한 요청 종류는 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`뿐이다. 앱은 이용 시작 때 이것으로 요청하고(USAGE_ASSISTANT, CONTENT_TYPE_SPEECH) 이용이 끝나면 놓는다. 로그 `FOCUS request granted=`, `FOCUS lost`, `FOCUS gained`.
- 가이드의 처리 권장: GAIN 재개, LOSS 정지, LOSS_TRANSIENT 일시정지, CAN_DUCK 볼륨 감소. 앱은 CAN_DUCK도 "다른 음성 안내"일 수 있어 멈춤으로 다룬다(features.md 공통 정책).
- Pleos TTS가 자체적으로 포커스를 요청하는지, 내비 안내 때 실제로 어떤 포커스 변화가 오는지는 문서에 없다 [실측필요]. 시연에서는 `sim.sh focus-loss|focus-gain`(로그 `SIM focus=LOST (시뮬레이션)`)으로 주입한다.
- 출처: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development

## 목소리 카드 녹음 기준 (F-08)
- 최대 8초. 0.5초 미만이거나 최대 진폭(`getMaxAmplitude`, 100ms마다)이 1000 미만이면 저장하지 않고 "한 번 더 크게!".
- 근거: F-01 실측에서 말할 때 `REC amp max=4722`, 무음 0. F-08 에뮬레이터(호스트 마이크 꺼짐)에서는 29~43 → 한 번 더 크게.
- [실측필요] 차 안에서 아이가 보통 크기로 말할 때의 진폭과 배경 소음 진폭. 기준값 1000은 그 뒤 조정한다.
