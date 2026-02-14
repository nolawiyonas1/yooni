# Yooni

Open-source AI phone agent. Voice in, phone actions out.

Yooni is an open-source, privacy-first voice assistant that takes natural language commands and executes them on your Android or iOS device.

## How it works

```
User speaks --> Whisper STT --> LLM formats action --> User confirms (voice) --> mobile-use executes
                                      ^                        |
                                      |--- user says "change X" (refine loop)
```

### Two layers

**Voice Layer** - Handles all user interaction. Captures speech via OpenAI Whisper (16kHz WAV), uses an LLM to interpret the command and format it into a clean action preview, then speaks it back to the user via OpenAI TTS for confirmation. The user can refine ("make it shorter", "say minutes not mins") until they're happy, then confirm to send.

**Phone Control Layer** - [mobile-use](https://github.com/minitap-ai/mobile-use) takes the confirmed natural language command and handles all device interaction. Android connects via ADB (USB debugging), iOS through simulators via Facebook's idb. mobile-use reads the screen, decides what to tap/type/swipe, and executes autonomously.

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
> **Yooni:** *sends via mobile-use* "Sent!"
