package com.example.yooni

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles mic recording, Whisper speech-to-text, and OpenAI TTS playback.
 *
 * Usage:
 *   val vm = VoiceManager(context, "your-openai-api-key")
 *   vm.startRecording()
 *   // ... user speaks ...
 *   vm.stopRecording()
 *   val text = vm.transcribe()   // sends audio to Whisper
 *   vm.speak("You said: $text")  // plays TTS
 */
class VoiceManager(
    private val context: Context,
    private val apiKey: String
) {
    companion object {
        private const val TAG = "VoiceManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var audioData = ByteArrayOutputStream()
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Start recording from the microphone.
     * Make sure RECORD_AUDIO permission is granted before calling this.
     */
    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        audioData = ByteArrayOutputStream()
        isRecording = true
        audioRecord?.startRecording()

        // Read audio in a background thread
        Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    audioData.write(buffer, 0, read)
                }
            }
        }.start()

        Log.d(TAG, "Recording started")
    }

    /**
     * Stop recording.
     */
    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "Recording stopped, ${audioData.size()} bytes captured")
    }

    /**
     * Send the recorded audio to OpenAI Whisper and return the transcription.
     * Uses raw HTTP to avoid okio version conflicts with the openai-kotlin library.
     */
    suspend fun transcribe(): String = withContext(Dispatchers.IO) {
        val pcmBytes = audioData.toByteArray()
        if (pcmBytes.isEmpty()) {
            Log.w(TAG, "No audio data to transcribe")
            return@withContext ""
        }

        // Convert raw PCM to WAV
        val wavFile = File(context.cacheDir, "recording.wav")
        writeWav(wavFile, pcmBytes, SAMPLE_RATE)
        val wavBytes = wavFile.readBytes()
        wavFile.delete()

        // Multipart POST to Whisper API
        val boundary = "----YooniBoundary${System.currentTimeMillis()}"
        val url = URL("https://api.openai.com/v1/audio/transcriptions")
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.doOutput = true

        DataOutputStream(connection.outputStream).use { out ->
            // file field
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"recording.wav\"\r\n")
            out.writeBytes("Content-Type: audio/wav\r\n\r\n")
            out.write(wavBytes)
            out.writeBytes("\r\n")

            // model field
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
            out.writeBytes("whisper-1\r\n")

            out.writeBytes("--$boundary--\r\n")
            out.flush()
        }

        val responseCode = connection.responseCode
        val responseBody = if (responseCode == 200) {
            connection.inputStream.bufferedReader().readText()
        } else {
            val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            Log.e(TAG, "Whisper API error ($responseCode): $error")
            connection.disconnect()
            return@withContext ""
        }
        connection.disconnect()

        val text = JSONObject(responseBody).optString("text", "")
        Log.d(TAG, "Transcription: $text")
        text
    }

    /**
     * Convert text to speech using OpenAI TTS and play it through the speaker.
     */
    suspend fun speak(text: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Speaking: $text")

        val url = URL("https://api.openai.com/v1/audio/speech")
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val body = JSONObject().apply {
            put("model", "tts-1")
            put("voice", "nova")
            put("input", text)
        }

        connection.outputStream.use { it.write(body.toString().toByteArray()) }

        if (connection.responseCode != 200) {
            val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            Log.e(TAG, "TTS API error (${connection.responseCode}): $error")
            connection.disconnect()
            return@withContext
        }

        // Save audio and play
        val tempFile = File(context.cacheDir, "tts_output.mp3")
        connection.inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        connection.disconnect()

        withContext(Dispatchers.Main) {
            // Release previous player if any
            mediaPlayer?.release()
            mediaPlayer = null

            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        it.release()
                        mediaPlayer = null
                        try {
                            if (tempFile.exists()) tempFile.delete()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error deleting temp file", e)
                        }
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        mp.release()
                        mediaPlayer = null
                        try {
                            if (tempFile.exists()) tempFile.delete()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error deleting temp file on error", e)
                        }
                        true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MediaPlayer", e)
                try {
                    if (tempFile.exists()) tempFile.delete()
                } catch (delEx: Exception) {
                    Log.e(TAG, "Error deleting temp file after init failure", delEx)
                }
            }
        }
    }

    /**
     * Write raw PCM bytes as a WAV file with proper headers.
     */
    private fun writeWav(file: File, pcmData: ByteArray, sampleRate: Int) {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val headerSize = 44

        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(headerSize).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                // RIFF header
                put("RIFF".toByteArray())
                putInt(dataSize + headerSize - 8)
                put("WAVE".toByteArray())
                // fmt chunk
                put("fmt ".toByteArray())
                putInt(16) // chunk size
                putShort(1) // PCM format
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort((channels * bitsPerSample / 8).toShort()) // block align
                putShort(bitsPerSample.toShort())
                // data chunk
                put("data".toByteArray())
                putInt(dataSize)
            }
            out.write(header.array())
            out.write(pcmData)
        }
    }
}
