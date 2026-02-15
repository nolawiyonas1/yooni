package com.example.yooni

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends confirmed commands to the Raspberry Pi server which then
 * passes them to mobile-use for phone control.
 *
 * Usage:
 *   val pi = PiClient("192.168.1.100", 8080)
 *   val success = pi.sendCommand("Send a text to Mom: 'I'll be there in 10 minutes.'")
 */
class PiClient(
    private val host: String,
    private val port: Int = 8080
) {
    companion object {
        private const val TAG = "PiClient"
    }

    /**
     * Send a confirmed command to the Pi for execution.
     * Returns true if the Pi acknowledged the command.
     */
    suspend fun sendCommand(command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/execute")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 30000 // mobile-use may take a while

            val body = """{"command": "${command.replace("\"", "\\\"")}"}"""

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Pi responded with code: $responseCode")

            connection.disconnect()
            responseCode == 200
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command to Pi: ${e.message}")
            false
        }
    }

    /**
     * Check if the Pi server is reachable.
     */
    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/ping")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000

            val reachable = connection.responseCode == 200
            connection.disconnect()

            Log.d(TAG, "Pi reachable: $reachable")
            reachable
        } catch (e: Exception) {
            Log.e(TAG, "Pi not reachable: ${e.message}")
            false
        }
    }
}
