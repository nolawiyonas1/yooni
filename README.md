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
├── android/                     # Native Android app (Kotlin)
│   ├── app/
│   │   └── src/main/
│   │       ├── java/.../yooni/
│   │       │   ├── WakeWordService.kt    # Porcupine "Hey Yooni" listener
│   │       │   ├── VoiceManager.kt       # Recording, Whisper STT, TTS playback
│   │       │   ├── ActionFormatter.kt    # LLM formats raw command into clean action
│   │       │   ├── ConfirmationLoop.kt   # Confirm/refine loop before execution
│   │       │   ├── PiClient.kt           # Sends confirmed commands to Raspberry Pi
│   │       │   └── MainActivity.kt
│   │       └── res/
│   └── build.gradle.kts
├── pi/                          # Raspberry Pi server
│   ├── server.py                # WebSocket/HTTP server receiving commands
│   └── executor.py              # Passes commands to mobile-use
├── voice.py                     # Standalone voice module (dev/testing)
├── requirements.txt
└── .env
```
