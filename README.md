# UNO P2P — Serverless Multiplayer UNO for Android (with Voice)

![Android CI](https://github.com/23f1000024/LLM_Create/actions/workflows/android.yml/badge.svg?branch=claude/p2p-uno-game-android-wysth6)

A native Android UNO-style card game where **one player's phone is the host**.
The host provides *temporary* local signaling only to bootstrap **direct WebRTC**
connections; after that, **all game data and voice run peer-to-peer**. There is
**no dedicated signaling server, no game server, no cloud backend, and no
database**. The primary supported mode is **same-Wi-Fi LAN**.

> Read [`NETWORKING.md`](NETWORKING.md) for exactly how connections are formed
> and — honestly — what does and doesn't work across the Internet.

## Features

- Host-authoritative UNO rules engine (108-card deck, Skip/Reverse/Draw Two,
  Wild/Wild Draw Four with bluff challenge, reshuffle, UNO calls, win detection).
- Clients are never trusted: the host validates every action and sends each
  player only **their** hand plus opponents' **counts** (no hand leaks).
- Embedded **Ktor** signaling on the host; **WebRTC** DataChannel for game
  messages and **WebRTC audio mesh** for voice (2–4 players).
- **QR-code joining** (or manual `ip:port` + room code), on-device scanning.
- Transparent **reconnection** to the same seat; host-leaves ends the game.
- **Offline hot-seat** mode: play 2–4 local players on one device (no network).
- **Network debug** screen and structured, filterable logs.

## Module layout

```
engine/   Pure Kotlin/JVM. UNO rules + wire protocol + host/client session logic.
          NO Android/Google dependencies → builds and unit-tests anywhere.
app/      Android app. WebRTC, Ktor signaling, Compose UI, voice, QR, camera.
```

The `engine` module is the tested heart of the game. It has **42+ JUnit tests**
covering deck/legality/turn-order/action cards/wild+WD4 challenge/reshuffle/win/
UNO/invalid+duplicate actions, plus an in-memory transport that drives a real
host↔clients round through the actual JSON codec to verify host authority,
private-hand redaction, and reconnect resync.

## Get the APK without building

Every push runs **GitHub Actions** (`.github/workflows/android.yml`), which runs
the engine tests and builds the debug APK on a runner where Google Maven is
reachable, then publishes it.

**Easiest — from Releases** (no login needed):
the **[`debug-latest`](../../releases/tag/debug-latest)** pre-release always
carries the newest **`uno-p2p-debug.apk`**. Download it and install with
`adb install -r uno-p2p-debug.apk`, or open it on-device with "install from
unknown sources" enabled.

**Or from the workflow artifact:** Actions tab → latest green **Android CI** run
→ **Artifacts** → `uno-p2p-debug-apk` (this one is a zip and needs a login).

You can also trigger a build yourself from the Actions tab (**Run workflow**).

## Build & run

### Prerequisites
- **JDK 17+**, **Android SDK** (platform 34, build-tools 34), and a machine that
  can reach **Google's Maven** (`dl.google.com` / `maven.google.com`) for the
  Android Gradle Plugin, AndroidX, CameraX, ML Kit and the WebRTC artifact.
- Android Studio (Ladybug or newer) is the easiest path.

### Run the engine tests (no Android SDK required)
```bash
./gradlew :engine:test
```
This resolves only from Maven Central and runs the full rules/protocol/session
suite — a good first check that the core works.

### Build the Android APK (needs the Android SDK + Google Maven)
```bash
# Point Gradle at your SDK (Android Studio does this automatically):
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```
Install on a phone: `adb install -r app/build/outputs/apk/debug/app-debug.apk`,
or just press **Run** in Android Studio.

> **Note on the WebRTC dependency.** The app uses the Maven-published prebuilt
> `io.github.webrtc-sdk:android` (`org.webrtc` API). If the pinned version in
> `gradle/libs.versions.toml` is unavailable in your environment, bump it to the
> latest published build — no code changes are needed.

## How to play

1. **Phone A** → *Create Game* → pick players/rules → you get a **room code**,
   **host address**, and a **QR code**.
2. **Phones B/C/D** → *Join Game* → **Scan QR** (or type the address + room code)
   → enter a nickname → *Join*.
3. Once everyone shows **connected**, the host taps **Start Game**.
4. Play cards, draw, call **UNO**, pick colors for wilds. Tap **Mic On** to talk.
5. Everything after connect is **direct peer-to-peer** — verify on the **Network
   Debug** screen that signaling is idle while data/voice are `CONNECTED`.

All phones must be on the **same Wi-Fi/hotspot** for the supported LAN mode.

## What this project does NOT do (by design)

- No Firebase/Supabase/Mongo/SQL/Redis/cloud functions; live game state is in
  memory only. Only your nickname/volume preferences are stored locally.
- No hidden cloud relay. If a direct connection can't be formed (e.g. two phones
  on different Internet networks behind NAT), the app **says so** instead of
  faking it — see `NETWORKING.md`.
- No host migration in v1 (host leaving ends the game); the architecture leaves
  room to add it later.

## Development status / honesty note

The `engine` module is fully built **and tested here**. The `app` module is
**source-complete** and written against the real WebRTC/Ktor/Compose/CameraX
APIs, but the APK must be compiled in an environment with Android SDK + Google
Maven access (the sandbox this was authored in blocks Google's servers by
policy). Card animations are minimal in v1; the game state updates independently
of any animation. Test on real devices per the phases in the project plan.
