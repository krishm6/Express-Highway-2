# Express Highway 2 (native Android)

A DSP-themed hill-climb racer written in **Kotlin with no WebView**: a `SurfaceView` game loop
drawing on the Android `Canvas`, native Android screens, and Android's `SpeechRecognizer` for voice driving.
No third-party dependencies.

## Build & run
1. Open this folder in **Android Studio** (Koala / 2024.1 or newer). Let Gradle sync (it downloads Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24).
2. Plug in a phone (USB debugging on) or start an emulator, then press **Run**.
3. For an APK: *Build > Build Bundle(s) / APK(s) > Build APK(s)*.
   Command line: run `gradle wrapper` once, then `./gradlew assembleDebug`.

Requires Android 7.0+ (minSdk 24). The game runs in landscape.

## Controls
* **GAS / BRAKE** circles (multi-touch). In the air, GAS tilts the nose up and BRAKE tilts it down.
* **MIC** chip (top right): voice control. Say **"forward"** (or go / gas / drive) to accelerate,
  **"brake"** (or stop / slow) to stop, **"neutral"** (or coast) to release. Commands latch until the next one.
* **+ / -** chips (Sampling Valley and Filter Lab): change the sampling rate / filter cutoff live.
* **II**: back to the menu.

## Modes
| Mode | DSP idea | What you do |
|---|---|---|
| Fourier Highway | Fourier series | Road = sum of harmonics; a new one fades in every 1200 px |
| Sampling Valley | Sampling, Nyquist, aliasing | Road only exists at samples (Ts apart). Dashed line = true signal. Raise fs until Ts <= 56 px |
| Filter Lab | Low-pass filtering | Choose how many harmonics pass. Lower cutoff = smoother, higher = more points per coin |
| Quantization Quarry | ADC bits | Road is rounded to n bits; every 1500 px you gain a bit and the steps get finer |

The DSP Handbook and a practice quiz are in the main menu. Nothing interrupts a run.

## Voice control notes
* Uses `android.speech.SpeechRecognizer` (needs `RECORD_AUDIO`, requested on first MIC tap).
* Most devices use Google's recognizer, which may need an internet connection; some phones beep when listening restarts.
* If a device has no recognition service installed, the app says so instead of crashing.

## Code map
```
game/Terrain.kt   DSP road generator for the four modes (pure Kotlin)
game/Game.kt      car physics, fuel, pickups, scoring (pure Kotlin, JVM-testable)
game/Content.kt   handbook lessons, quiz questions, mode descriptions
GameView.kt       SurfaceView renderer, 60 fps loop, touch pedals and HUD
VoiceController.kt  continuous SpeechRecognizer -> FORWARD / BRAKE / NEUTRAL
MainActivity.kt   native menu, mode picker, handbook, quiz and game-over screens
```
