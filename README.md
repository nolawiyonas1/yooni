# Yooni

Open-source AI phone agent. Voice in, phone actions out.

Yooni is an open-source, privacy-first voice assistant that takes natural language commands and executes them on your Android or iOS device.

## Architecture

```
Voice Input (mic) --> Whisper STT --> Agent (LLM planner) --> mobile-use (phone control)
                                           |                        |
                                     live reasoning          taps/swipes/types
                                           |                        |
                                      TTS response <-- screen state feedback
```

### Three layers

**Voice Layer** - Captures speech from the mic, transcribes via OpenAI Whisper (16kHz WAV), and speaks responses back via OpenAI TTS (model: `tts-1`, voice: `nova`). Runs on the host machine.

**Agent Layer** - LLM (GPT-4o) receives the transcribed command, breaks it into a step-by-step plan of phone actions, sends each action to mobile-use, checks the screen result, and decides the next move. Handles failures like unexpected popups or wrong screens.

**Phone Control Layer** - [mobile-use](https://github.com/minitap-ai/mobile-use) handles device interaction. Android connects via ADB (USB debugging). iOS works through simulators via Facebook's idb. The agent reads the accessibility tree and screenshots to understand screen state.

