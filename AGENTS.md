# Silica Assistant

Android voice-controlled assistant with a floating overlay character ("waifu").

## Build

```sh
./gradlew :app:assembleDebug
```

- Gradle 8.2, AGP 8.2.2, Kotlin 1.9.22, Compose BOM 2024.02.00, Compose Compiler 1.5.8
- compileSdk / targetSdk = 34, minSdk = 24, jvmTarget = 17

## Architecture

Single-module app (`:app`). Packages under `com.silica.assistant`:

| Package | Key files | Role |
|---|---|---|
| `core/` | `CommandManager`, `CommandNormalizer`, `CommandAliases`, `IntentController`, `CommandHistoryManager` | Voice command parsing and app intent dispatch |
| `ui/` | `MainScreen.kt`, `components/` (4 real composables), `viewmodel/`, `state/` | Jetpack Compose UI |
| `service/` | `OverlayService.kt` | System overlay (TYPE_APPLICATION_OVERLAY) rendering waifu sprite |
| `overlay/` | `WaifuStateManager`, `WaifuExpressionController`, `WaifuState` | State-driven expression switching (IDLE / HAPPY / LISTENING) |
| `model/` | `CommandLog.kt` | Data model |

## Gotchas

- **Speech recognition** uses `GoogleRecognitionService` via `SpeechRecognizer.createSpeechRecognizer(context, component)` targeting `com.google.android.voicesearch.serviceapi.GoogleRecognitionService` under `com.google.android.googlequicksearchbox`. Language hardcoded to `id-ID` (Indonesian). Requires `RECORD_AUDIO` and `INTERNET` permissions.
- **Command matching** is substring `contains()` against `CommandAliases.aliases`. The longest matching alias wins. Input is lowercased + trimmed first.
- **Overlay** uses `startService()`, not `startForegroundService()`, despite `foregroundServiceType="mediaProjection"` in manifest. Requires `SYSTEM_ALERT_WINDOW` permission.
- **Drawables**: `mybinik.png` (IDLE), `mybinikmangap.png` (HAPPY), `mybinikmendengarkan.png` (LISTENING). Overlay icon is 120dp x 120dp ImageView in `res/layout/overlay_view.xml`.
- **Expression update** runs every 200ms via `Handler.postDelayed` loop in `OverlayService`. Touch: tap → HAPPY for 800ms then IDLE; drag → LISTENING.
- **ViewModel**: `AssistantViewModel` + `AssistantUiState` (commandText, isListening). No DI framework.
- **No tests**, no CI, no lint config. Only verification is building + running on device/emulator.
