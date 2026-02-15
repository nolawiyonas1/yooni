package com.example.yooni

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yooni.ui.theme.YooniTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var voiceManager: VoiceManager
    private lateinit var actionFormatter: ActionFormatter

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Mic permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val apiKey = BuildConfig.OPENAI_API_KEY
        voiceManager = VoiceManager(this, apiKey)
        actionFormatter = ActionFormatter(apiKey)

        micPermission.launch(Manifest.permission.RECORD_AUDIO)

        enableEdgeToEdge()
        setContent {
            YooniTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VoiceTestScreen(
                        voiceManager = voiceManager,
                        actionFormatter = actionFormatter,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceTestScreen(
    voiceManager: VoiceManager,
    actionFormatter: ActionFormatter,
    modifier: Modifier = Modifier
) {
    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Tap the button and speak") }
    var transcriptionText by remember { mutableStateOf("") }
    var actionPreviewText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Yooni",
            fontSize = 32.sp,
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = statusText,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (!isRecording) {
                    isRecording = true
                    statusText = "Listening..."
                    transcriptionText = ""
                    // Don't clear actionPreviewText here so we can refine it
                    voiceManager.startRecording()
                } else {
                    isRecording = false
                    statusText = "Thinking..."
                    voiceManager.stopRecording()

                    scope.launch {
                        val userText = voiceManager.transcribe()
                        transcriptionText = userText
                        
                        if (userText.isNotBlank()) {
                            val response = if (actionPreviewText.isEmpty()) {
                                // New conversation
                                statusText = "Formatting..."
                                actionFormatter.format(userText)
                            } else {
                                // Follow-up / refinement
                                statusText = "Refining..."
                                actionFormatter.refine(userText)
                            }
                            
                            actionPreviewText = response
                            statusText = "Confirming..."
                            voiceManager.speak(response)
                            statusText = "Ready to execute?"
                        } else {
                            statusText = "Didn't catch that."
                        }
                    }
                }
            },
            modifier = Modifier.size(120.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (isRecording) "Stop" else "Speak",
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (actionPreviewText.isNotEmpty()) {
            TextButton(onClick = { 
                actionPreviewText = "" 
                statusText = "New conversation started"
            }) {
                Text("Start Over")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (transcriptionText.isNotEmpty()) {
            Text(
                text = "You said:",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = transcriptionText,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (actionPreviewText.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Yooni will:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = actionPreviewText,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
