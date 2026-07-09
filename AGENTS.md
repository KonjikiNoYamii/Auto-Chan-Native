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
|---|---|---|---|
| `core/` | `CommandManager`, `CommandNormalizer`, `CommandAliases`, `IntentController`, `CommandHistoryManager` | Voice command parsing and app intent dispatch |
| `core/voice/` | `VoiceManager` | Speech recognition (default recognizer, no explicit component) |
| `core/action/` | `Action`, `ActionMapper`, `ActionExecutor` | Sealed-class action dispatch |
| `core/media/` | `MediaController` | Simulated media key events via `AudioManager.dispatchMediaKeyEvent` |
| `core/overlay/` | `OverlayEventBus` | Simple callback bus to overlay |
| `core/parser/` | `SearchCommandParser` | Detects "search/cari/carikan/google" prefixes, dispatches Google search |
| `core/llm/` | `MoodManager` | Relationship + absence system: affinity, XP, quests, gifts, streaks, inventory, mood, stamina, absence awareness tier. `markUserReachedOut()` updates `lastInteractionTime` only on user-initiated actions (chat, voice, explicit command), NOT on overlay output — so absence detection works across sessions. `getAbsenceContext()` injects emotion-scaled prompt snippet based on tier + absence duration. |
| `core/llm/` | `MemoryManager` | Two-tier memory: (1) `user_memory_*` — AI-extracted user facts (deduplicated, synced). (2) `memory_*` — shared memory log (`logSharedMemory(summary, category)`) for quest/gift/affinity level-up/chat milestones. `getRecentMemories(days)` and `getRandomMemory()` for context recall. |
| `core/llm/` | `KtorLlmRepository` | AI provider with auto-fallback LocalGemini ↔ Gemini. `loadDbContext()` includes `user_memory_*`, `game_*`, and `memory_*` (shared) — last 10. `MAX_HISTORY_CONTEXT` = 10 messages for AI context window. Sync push after each successful chat. |
| `core/llm/db/` | `SilicaDatabase`, `ChatDao`, `UserFactDao`, `UserProfileDao`, `QuestDao`, `AchievementDao`, `FriendDao`, `SocialMessageDao` | Room database & DAOs |
| `core/auth/` | `AuthRepository` | Firebase auth + sync. `syncPush()` uploads profile, quests, facts (including all memory_*), achievements, chats, friends. `syncPull()` replaced local on login/startup. All data survives app data clear if logged in. |
| `ui/` | `MainScreen.kt`, `chat/ChatViewModel.kt`, `chat/ChatScreen.kt`, `components/`, `viewmodel/`, `state/` | Jetpack Compose UI. ChatViewModel calls `markUserReachedOut()` on send + `logSharedMemory()` for messages >50 chars. |
| `service/` | `OverlayService.kt` | System overlay (TYPE_APPLICATION_OVERLAY) rendering waifu sprite |
| `overlay/` | `WaifuStateManager`, `WaifuExpressionController`, `WaifuState`, `GameModeManager` | State-driven expression switching & game mode |
| `model/` | `CommandLog.kt`, `CommandResult.kt` | History data model |

## Gotchas

- **Speech recognition** has two independent paths:
  - `MainScreen.kt` explicitly targets `GoogleRecognitionService` via `SpeechRecognizer.createSpeechRecognizer(context, component)`.
  - `OverlayService.kt` and `VoiceManager` use the default recognizer (no component arg). Language hardcoded to `id-ID` (Indonesian). Requires `RECORD_AUDIO` + `INTERNET`.
- **Command matching** is token-scoring (`CommandNormalizer.score`), not substring `contains`. Each token in the alias scores 2 points; a full-phrase match adds 5. The highest-scoring command wins, not the longest alias.
- **Overlay** runs as a single foreground service (`OverlayService`). `VoiceForegroundService` has been merged into it. Requires `SYSTEM_ALERT_WINDOW`.
- **Drawables**: `mybinik.png` (IDLE), `mybinikmangap.png` (HAPPY), `mybinikmendengarkan.png` (LISTENING). Overlay icon is 120dp x 120dp ImageView in `res/layout/overlay_view.xml`.
- **Bubble sound**: `res/raw/pop.mp3` played via `MediaPlayer` on every bubble show.
- **Expression update** runs every **500ms** via `Handler.postDelayed` loop in `OverlayService`. Touch behavior: tap toggles LISTENING on/off; drag moves window; long-press (600ms) triggers `VoiceManager.start()`.
- **DI**: Koin via `AppModule.kt`. All Room DAOs are separate files under `core/llm/db/`.
- **Model package quirk**: `CommandResult.kt` declares `package com.silica.assistant.core.model` but lives in `model/` (not `core/model/`).
- **Database**: Room `SilicaDatabase` at version 13. Uses proper migration for future schema changes. `fallbackToDestructiveMigration` has been replaced.
- **ProGuard**: Pre-configured rules for JSch, uCrop, Room, Koin, Ktor, kotlinx.serialization, Firebase, and Coroutines. See `app/proguard-rules.pro`.
- **Absence awareness**: `lastInteractionTime` is updated ONLY on user-initiated actions (chat, voice command, explicit triggers). Spontaneous overlay comments (`generateActivityComment`, `describeScreen`) do NOT update it. This ensures absence detection works correctly across sessions.
- **Absence tiers**: All tiers share the same time thresholds — NONE (<24h), SHORT (1-3d), MEDIUM (3-7d), LONG (7d+). The difference is in the EMOTION (kuudere-scaled): strangers don't care, close tiers worry but hide it, soulmate's cold mask cracks.
- **Shared memory format**: `memory_YYYYMMDD_cat_N` key format (e.g. `memory_20240709_quest_0`). Stored in `user_facts` table with existing `UserFactDao`. Survives app data clear if logged in (Firebase sync).
- **Sync flow**: `syncPush()` called after chat, quest completion, gift, affinity change, and memory log. `syncPull()` on login and app startup. Firebase overwrites local on pull (full replace).
