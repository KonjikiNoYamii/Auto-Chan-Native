# Silica Assistant (Auto-Chan-Native)

Android voice-controlled assistant with a floating overlay character ("waifu").  
Control your phone and laptop hands-free via voice commands in Indonesian/English.

## 🛡️ Play Protect & Keamanan
Aplikasi ini diinstal secara manual (APK) dan menggunakan izin sensitif (Accessibility, Screen Capture) untuk bekerja sebagai asisten. Hal ini terkadang membuat Google Play Protect memberikan peringatan.

**Langkah jika muncul peringatan "Blocked by Play Protect":**
1. Klik **"More Details"** (Detail Selengkapnya).
2. Pilih **"Install Anyway"** (Tetap Instal).
3. Aplikasi ini aman dan hanya berkomunikasi dengan server AI (OpenRouter/Gemini) dan laptop Anda sendiri.

## 🔄 Cara Update
Aplikasi memiliki fitur pengecekan update otomatis. Jika muncul notifikasi update:
1. Klik tombol **Update** di aplikasi.
2. Tunggu proses download selesai.
3. Instal APK terbaru yang sudah diunduh.

## Features

### 🎤 Voice Commands
| Category | Commands | Description |
|---|---|---|
| Media | `play musik`, `next song`, `previous song` | Control music playback |
| Volume | `naikkan volume`, `mute`, `full volume` | Volume control |
| Brightness | `cerahkan layar`, `gelapkan layar`, `brightness maksimal` | Screen brightness |
| Apps | `buka whatsapp`, `open telegram`, `buka wa` | Launch any installed app |
| Quick Apps | `spotify`, `youtube`, `browser`, `chrome`, `settings` | Open specific apps directly |
| Search | `search anime`, `cari resep`, `google ...` | Google search |
| Knowledge | `tier list genshin`, `rekomendasi karakter ml` | Game tier lists (Genshin, ML, Valorant) |
| SSH | `ssh_status`, `ssh_connect`, `laptop_info` | Laptop remote control |
| Quest | `tambah tugas ...`, `selesaikan ...` | Quest & productivity tracking |
| Profile | `buka profil`, `afinitas` | User profile & relationship stats |
| Overlay | `start overlay`, `stop overlay` | Toggle waifu overlay |

### 🏆 Achievement System (100 Achievements)
Silica sekarang memiliki sistem pencapaian yang luas untuk menemani perjalanan Anda:
- **10 Kategori Utama**: Pekerja Keras, Pejuang Tangguh, Harmoni, Disiplin, Legenda, Dermawan, Kolektor, Interaktif, Teman Setia, dan Puncak Mood.
- **10 Tingkatan (Tiers)**: Setiap kategori memiliki 10 level progres (Perunggu, Perak, Emas, hingga Platinum).
- **Achievement Gallery**: Layar khusus untuk melihat semua koleksi badge dan progres Anda.
- **Real-time Notification**: Silica akan memberikan selamat langsung jika Anda membuka achievement baru!

### 👻 Overlay Waifu
- Floating character with 3 expressions: IDLE, HAPPY, LISTENING
- Tap → start voice recognition
- Long press (600ms) → voice command
- Drag → move overlay anywhere
- Bubble text shows command results
- Pop sound on every bubble
- **Absence awareness**: Yami reacts differently based on how long you've been gone (1-3d, 3-7d, 7d+) — scaled by relationship tier
- **Shared memories**: Yami logs quest completions, gifts, affinity milestones, and long chats — recalls them naturally in conversation

### 🔌 SSH Laptop Control
- Connect via SSH password
- Terminal: execute any Linux command, `cd` navigation with local path resolution
- File Manager: browse, upload, download files via SFTP
- File downloads saved to **`Downloads/SilicaAssistant/`** — visible in Downloads app, deletable like any normal file
- File detection accurately reflects device state — deleted files no longer show as "downloaded"
- Quick folders: Home, Documents, Downloads, Pictures, Music, Videos, Desktop
- Laptop Info: real-time monitoring (uptime, RAM, disk) with 3s polling
- Session health check — auto-detect connection drops
- Secure password input with show/hide toggle

### 🎨 Theme
- **Yami Theme** — dark mode
- Accent colors: DeepRose (merah) + Espresso (coklat)
- Status bar: SSH connection status, WiFi indicator

## Build

```sh
# Debug build
./gradlew :app:assembleDebug

# Release build & install
./gradlew :app:installRelease
```

Requires:
- Gradle 8.2, AGP 8.2.2, Kotlin 1.9.22
- Compose BOM 2024.02.00, Compose Compiler 1.5.8
- compileSdk / targetSdk = 34, minSdk = 24, jvmTarget = 17
- JSch 0.1.55 for SSH

### APK Location

| Variant | Path |
|---|---|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release | `app/build/outputs/apk/release/app-release.apk` |

## Permissions Required
- `SYSTEM_ALERT_WINDOW` — overlay waifu
- `RECORD_AUDIO` — voice recognition
- `INTERNET` — SSH, Google search
- `FOREGROUND_SERVICE` — overlay service
- `WRITE_SETTINGS` — brightness control
- `QUERY_ALL_PACKAGES` — app launcher (find & open any installed app)

## Architecture

```plaintext
com.silica.assistant/
├── core/
│   ├── CommandManager.kt       — Voice command dispatch
│   ├── CommandNormalizer.kt    — Token-scoring command matcher
│   ├── CommandAliases.kt       — Command alias definitions
│   ├── IntentController.kt     — App/action intent dispatch
│   ├── CommandHistoryManager   — Command history
│   ├── ssh/
│   │   ├── SshManager.kt       — SSH connect/exec/SFTP
│   │   ├── SshConnection.kt    — Data model
│   │   └── SshFile.kt          — File listing model
│   ├── actions/
│   ├── media/MediaController   — Simulated media key events
│   ├── overlay/OverlayEventBus — Event bus to overlay
│   ├── parser/SearchCommandParser — Google search detection
│   ├── llm/
│   │   ├── MoodManager.kt     — Affinity, quests, absence system
│   │   ├── MemoryManager.kt   — User facts + shared memory log
│   │   ├── KtorLlmRepository  — AI provider (LocalGemini / Gemini)
│   │   ├── LlmConfig.kt       — API keys, model, personality
│   │   └── db/                — Room DAOs
│   └── system/
│       ├── AppLauncher.kt      — Search & launch any app
│       ├── BrightnessController
│       └── VolumeController
├── ui/
│   ├── MainScreen.kt           — Home with chips, voice, history
│   ├── chat/
│   │   ├── ChatScreen.kt       — Chat with Yami (markdown, typewriter)
│   │   ├── ChatViewModel.kt    — Send/receive, memory logging, absence
│   │   └── SocialChatScreen.kt — Friend-to-friend messaging
│   ├── ssh/
│   │   ├── SshScreen.kt        — Connection form, Terminal, Files
│   │   └── LaptopInfoScreen.kt — Real-time laptop monitor
│   ├── guide/GuideScreen.kt    — Feature documentation
│   ├── customize/CustomizeScreen.kt — Personality & character settings
│   ├── components/             — Reusable composables
│   ├── viewmodel/              — AssistantViewModel
│   └── state/                  — UiState models
├── service/
│   └── OverlayService.kt       — System overlay rendering (includes voice foreground)
├── overlay/
│   ├── WaifuStateManager       — Expression state machine
│   ├── WaifuExpressionController
│   └── WaifuState              — IDLE / HAPPY / LISTENING
└── model/
    └── CommandLog.kt
```

## Voice Command Details

### App Launcher
- Prefix dengan `buka ` (Indonesia) atau `open ` (English):
  ```
  buka whatsapp / open whatsapp
  buka telegram
  buka discord
  ```
- Tanpa prefix (fallback):
  ```
  whatsapp  → otomatis coba buka app
  telegram  → coba buka app, gagal → Google search
  ```
- Alias umum: `wa` → WhatsApp, `ig` → Instagram, `fb` → Facebook
- **Label + PackageName matching** — semua app terinstall terbaca

### Quick App Commands
```
spotify      → Spotify
youtube      → YouTube
browser / chrome → Browser
settings / pengaturan → Settings
```

### Search
```
search ... / cari ... / carikan ... / google ...
```
Membuka Google search dengan query.

### Knowledge (Game Tier List)
```
tier list genshin
tier list ml
tier list valorant
rekomendasi karakter genshin
```

### SSH Commands
```
ssh_status             → Cek koneksi SSH
ssh_connect            → Buka halaman SSH (navigasi + foreground)
ssh_disconnect         → Putus koneksi
laptop_info            → Info uptime, RAM, disk (via bubble)
```

### Media & System
```
play musik / pause musik / next song / previous song
naikkan volume / turunkan volume / mute / full volume
cerahkan layar / gelapkan layar / brightness maksimal
```

## Planned Features (Roadmap)

- [ ] **ANSI escape parser** — terminal output dengan warna/bold nyata (QR code bisa tampil)
- [ ] **Text-to-speech** — waifu baca balasan bubble
- [ ] **Notification listener** — baca notifikasi via voice
- [ ] **Proactive AI** — Yami initiate conversation based on time, mood, or random recall
- [ ] **Voice-to-terminal** — ucapkan command langsung ke SSH terminal
- [ ] **SSH key auth** — koneksi pakai key
- [ ] **Game analysis** — AI analyze screenshot + state secara real-time
- [ ] **Multiple SSH connections** — simpan & switch antar profile

## Notes
- Speech recognition uses `id-ID` language (Indonesian). English commands also work.
- Two independent speech paths:
  - `MainScreen` uses Google Recognition Service explicitly
  - `OverlayService` + `VoiceManager` use default recognizer
- SSH session persists across screen navigation.
- Laptop IP: `192.168.1.7`, user: `goldendarkness` (example — customize as needed).
