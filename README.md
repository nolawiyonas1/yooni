# Yooni

Open-source AI phone agent. Voice in, phone actions out.

Yooni is an open-source, privacy-first voice assistant that takes natural language commands and executes them on your Android device.

## Architecture

```
Android App (Kotlin)                         Raspberry Pi
  - Wake word detection ("Hey Yooni")          - mobile-use (controls phone via ADB)
  - Records speech
  - Whisper STT (openai-kotlin)
  - LLM formats action (openai-kotlin)
  - TTS speaks back for confirmation
  - User confirms/refines (voice loop)
  - Sends confirmed command ------>  websocket/HTTP ------> executes on phone
```

### Two layers

**Voice Layer (Android App)** - Native Kotlin app that runs on the phone. Listens for the "Hey Yooni" wake word using [Porcupine](https://picovoice.ai/platform/porcupine/) (on-device, no network). Once triggered, records speech, transcribes via OpenAI Whisper, uses an LLM to format the command into a clean action preview, and speaks it back via OpenAI TTS for confirmation. User can refine until satisfied, then confirms to execute.

**Phone Control Layer (Raspberry Pi)** - A Raspberry Pi connected to the Android phone via USB. Runs [mobile-use](https://github.com/minitap-ai/mobile-use) which receives the confirmed command and handles all device interaction over ADB. Reads the screen, decides what to tap/type/swipe, and executes autonomously.

## Design

[Figma](https://www.figma.com/make/r1MwLN4N2Aw0EqVVmqyRjl/Untitled?p=f)

## Example flow

> **User:** "Hey Yooni, text Mom that I'll be there in 10 mins"
>
> **Yooni:** "I'll send this to Mom: 'Hey! I'll be there in about 10 minutes.' Sound good?"
>
> **User:** "Add 'do you need anything?'"
>
> **Yooni:** "Got it: 'Hey! I'll be there in about 10 minutes. Do you need anything?' Ready to send?"
>
> **User:** "Yes"
>
> **Yooni:** *sends to Pi, mobile-use executes* "Sent!"

## File structure

```
yooni/
├── android/                           # Native Android app (Kotlin)
│   ├── app/
│   │   ├── build.gradle.kts           # App dependencies (openai-kotlin, ktor, porcupine)
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       └── java/com/example/yooni/
│   │           ├── MainActivity.kt        # App entry point
│   │           ├── WakeWordService.kt     # Porcupine "Hey Yooni" listener (planned)
│   │           ├── VoiceManager.kt        # Recording, Whisper STT, TTS (planned)
│   │           ├── ActionFormatter.kt     # LLM formats command (planned)
│   │           ├── ConfirmationLoop.kt    # Confirm/refine loop (planned)
│   │           ├── PiClient.kt            # Sends commands to Pi (planned)
│   │           └── ui/theme/
│   │               ├── Color.kt
│   │               ├── Theme.kt
│   │               └── Type.kt
│   ├── build.gradle.kts               # Root Gradle config
│   └── settings.gradle.kts
├── pi/                                # Raspberry Pi server (planned)
│   ├── server.py                      # Receives commands from Android app
│   └── executor.py                    # Passes commands to mobile-use
└── README.md
```
