# Silica Assistant

Android voice-controlled assistant with a floating overlay character ("waifu").

## Build

```sh
./gradlew :app:assembleDebug
```

- Gradle 8.2, AGP 8.2.2, Kotlin 1.9.22, Compose BOM 2024.02.00, Compose Compiler 1.5.8
- compileSdk / targetSdk = 34, minSdk = 24, jvmTarget = 17
- No tests, no CI, no lint config. Only verification is building + running.

## Architecture

Single-module app (`:app`). Packages under `com.silica.assistant`:

| Package | Key files | Role |
|---|---|---|
| `core/` | `CommandManager`, `CommandNormalizer`, `CommandAliases`, `IntentController`, `CommandHistoryManager` | Voice command parsing and app intent dispatch |
| `core/voice/` | `VoiceManager` | Speech recognition (default recognizer, no explicit component) |
| `core/actions/` | `Action`, `ActionMapper`, `ActionExecutor` | Sealed-class action dispatch |
| `core/media/` | `MediaController` | Simulated media key events via `AudioManager.dispatchMediaKeyEvent` |
| `core/overlay/` | `OverlayEventBus` | Simple callback bus to overlay |
| `core/parser/` | `SearchCommandParser` | Detects "search/cari/carikan/google" prefixes, dispatches Google search |
| `core/knowlegde/` | `KnowledgeEngine`, `KnowledgeParser`, `KnowledgeQuery` | Hardcoded game tier/character Q&A (Genshin, ML, Valorant) |
| `ui/` | `MainScreen.kt`, `components/`, `viewmodel/`, `state/` | Jetpack Compose UI (4 composables) |
| `service/` | `OverlayService.kt` | System overlay (TYPE_APPLICATION_OVERLAY) rendering waifu sprite |
| `overlay/` | `WaifuStateManager`, `WaifuExpressionController`, `WaifuState` | State-driven expression switching (IDLE / HAPPY / LISTENING) |
| `model/` | `CommandLog.kt` | History data model |

## Gotchas

- **Speech recognition** has two independent paths:
  - `MainScreen.kt` explicitly targets `GoogleRecognitionService` via `SpeechRecognizer.createSpeechRecognizer(context, component)`.
  - `OverlayService.kt` and `VoiceManager` use the default recognizer (no component arg). Language hardcoded to `id-ID` (Indonesian). Requires `RECORD_AUDIO` + `INTERNET`.
- **Command matching** is token-scoring (`CommandNormalizer.score`), not substring `contains`. Each token in the alias scores 2 points; a full-phrase match adds 5. The highest-scoring command wins, not the longest alias.
- **Overlay** uses `startService()`, not `startForegroundService()`, despite `foregroundServiceType="mediaProjection"` in manifest. Requires `SYSTEM_ALERT_WINDOW`.
- **Drawables**: `mybinik.png` (IDLE), `mybinikmangap.png` (HAPPY), `mybinikmendengarkan.png` (LISTENING). Overlay icon is 120dp x 120dp ImageView in `res/layout/overlay_view.xml`.
- **Bubble sound**: `res/raw/pop.mp3` played via `MediaPlayer` on every bubble show.
- **Expression update** runs every **100ms** via `Handler.postDelayed` loop in `OverlayService`. Touch behavior: tap toggles LISTENING on/off; drag moves window; long-press (600ms) triggers `VoiceManager.start()`.
- **ViewModel**: `AssistantViewModel` + `AssistantUiState` (commandText, isListening). No DI framework.
- **Model package quirk**: `CommandResult.kt` declares `package com.silica.assistant.core.model` but lives in `model/` (not `core/model/`).
- **Typo directory**: `core/knowlegde/` is misspelled (missing 'd'); package declaration uses correct `knowledge`.
- **Actions package mismatch**: directory is `core/actions/` but files declare `package com.silica.assistant.core.action` (no 's').
- **Double .kt extension**: `AssistantViewModel.kt.kt`.
