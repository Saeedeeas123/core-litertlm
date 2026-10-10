# :core:litertlm — safe offline LiteRT-LM loader (Premade Module)

Reusable Android library module that loads and runs `.litertlm` models **fully on-device**
through Google's [LiteRT-LM](https://ai.google.dev/edge/litert-lm) Kotlin API.

Extracted from the working `litertchat` app (verified on device by Saeed, 2026-10-10) and hardened
into an app-agnostic module: the "safe" part is that every failure mode found while building the
app is now a guarded, catchable error instead of a crash.

## What makes it "safe"

| Guard | Why it exists |
|---|---|
| **Java, not Kotlin** | LiteRT-LM 0.18.0 ships Kotlin **2.4** metadata + Java **21** bytecode. Kotlin < 2.4 projects cannot read the metadata; Java interop is unaffected, so this module works from any consuming project. |
| **No `$default` bridges** | Kotlin's default-argument bridges are synthetic and `javac` cannot resolve them. Every optional API parameter is passed explicitly here. |
| **Magic-header check before loading** | The native engine **aborts** on a non-LiteRT-LM file instead of throwing. The module validates the `.litertlm` extension and the `LITERTLM` magic in Java first. |
| **Main-thread refusal** | `load()` and generation throw `ON_MAIN_THREAD` rather than freezing the UI. |
| **Serialized generations** | A single-flight lock prevents concurrent calls into a native session that is not thread-safe. |
| **Race-free holder** | `LitertLmRuntime` retires the old engine *before* loading a new one, so a close/reload can never hand out a dead conversation. |
| **Streaming-tolerant accumulator** | Works whether the library streams deltas or cumulative snapshots. |
| **Actionable exceptions** | Every failure carries a `Kind` (MODEL_MISSING / MODEL_UNREADABLE / MODEL_NOT_LITERTLM / NOT_ENOUGH_MEMORY / LOAD_FAILED / NOT_LOADED / CLOSED / ON_MAIN_THREAD / GENERATION_FAILED) plus a message that says what to do. |

## Public surface

- `LitertLmEngine(Context, LitertLmConfig) : AutoCloseable` — `load()`, `isLoaded()`,
  `chat(prompt)` (blocking, returns the answer), `chatStreaming(prompt, listener)`, `cancel()`,
  `close()`.
- `LitertLmRuntime` — process-wide holder: `acquire(context, config)`, `release()`, `isLoaded()`,
  `loadedPath()`. Load once, reuse across activity recreation.
- `LitertLmConfig` — builder: `backend(CPU|GPU)`, `cacheDir`, `topK`, `topP`, `temperature`, `seed`,
  `systemInstruction`, `maxOutputTokens`, `failFastOnLowMemory`. Pure Java, clamped by `normalized()`.
- `LitertLmChatListener` — `onDelta(textSoFar)`, `onComplete(fullText)`, `onError(throwable)`.
- `LitertLmModels` / `LitertLmModelFormats` — pre-flight validation (extension, magic, readability).
- `LitertLmMemory` — RAM estimate + `fitsIn`; `failFastOnLowMemory` is **opt-in** because weights are
  mapped from the file, so file size is not the resident requirement (a 3.66 GB model is verified
  working on an 8 GB device).
- `LitertLmException` — with `Kind`.

## Integration recipe

1. Copy `core/litertlm/` into your project and add `include(":core:litertlm")`.
2. The module depends on `com.google.ai.edge.litertlm:litertlm-android:0.18.0` as `implementation`
   (deliberately not `api`: the library's Kotlin 2.4 metadata must never reach a consumer's Kotlin
   compile classpath).
3. **Build with JDK 21** — `JAVA_HOME=/opt/jdk21 ./gradlew assembleDebug`. `javac` refuses to read
   the library's Java 21 class files on JDK 17.
4. The consuming app needs `MANAGE_EXTERNAL_STORAGE` (plus `READ_EXTERNAL_STORAGE` with
   `maxSdkVersion="32"`) when the model lives in shared storage, and these two entries inside
   `<application>` for the GPU backend:
   ```xml
   <uses-native-library android:name="libvndksupport.so" android:required="false"/>
   <uses-native-library android:name="libOpenCL.so" android:required="false"/>
   ```
5. Load off the main thread, then chat:
   ```java
   new Thread(() -> {
       LitertLmEngine engine = LitertLmRuntime.acquire(context,
               LitertLmConfig.builder("/storage/emulated/0/gemma-3n-E2B-it-int4.litertlm")
                       .backend(LitertLmConfig.Backend.CPU)
                       .build());
       String answer = engine.chat("What is the capital of France?");
   }).start();
   ```

## Verified

- 2026-10-10: `:core:litertlm:testDebugUnitTest` — **28 tests, 0 failures, 0 errors**
  (config clamping incl. NaN, validation kinds, magic/extension detection incl. a ZIP `.task`
  bundle, model pre-flight errors, `/proc/meminfo` parsing, RAM boundary maths, streaming
  accumulator in both delta and cumulative mode).
- 2026-10-10: `:core:litertlm:assembleDebug` → **BUILD SUCCESSFUL in 34s**, AAR `litertlm-debug.aar`.
- The underlying approach is **device-confirmed** by Saeed (the `litertchat` app it was extracted
  from ran and answered correctly with `gemma-3n-E2B-it-int4.litertlm` on CPU).

## Limits / known gaps

- **The module itself has not been exercised on a device as a library** — the code path is the one
  Saeed verified inside the app, but the extraction has only been proven by JVM tests + a green
  build. A consuming app must confirm it.
- No streaming *token* callbacks beyond what LiteRT-LM provides; no tool-calling, no multimodal
  (vision/audio) wiring yet, although `EngineConfig` exposes `visionBackend`/`audioBackend`.
- No model download/import helper (that is a separate concern; `litertchat` reads a path directly).
- `chat()` has no timeout by design (a 3B-class model on a phone CPU legitimately takes minutes);
  callers own cancellation via `cancel()` or thread interruption.
- One generation at a time per engine instance.

## Toolchain

AGP 8.5.2 · Gradle 8.7 · compileSdk 35 · minSdk 24 · buildTools 35.0.1 · **JDK 21** ·
`litertlm-android 0.18.0`. `settings.gradle.kts` lists the Aliyun Maven mirrors first because
`dl.google.com` is blocked on this network.
