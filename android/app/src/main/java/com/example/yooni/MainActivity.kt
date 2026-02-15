package com.example.yooni

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yooni.BuildConfig
import com.example.yooni.ui.theme.YooniBlue
import com.example.yooni.ui.theme.YooniPink
import com.example.yooni.ui.theme.YooniTextDark
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
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.White
                ) { innerPadding ->
                    VoiceTestScreen(
                        voiceManager = voiceManager,
                        actionFormatter = actionFormatter,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Color.White)
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

    val logoGradient = Brush.linearGradient(
        colors = listOf(YooniPink, YooniBlue)
    )
    
    // Breathing animation for idle state
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val buttonSize by animateDpAsState(
        targetValue = if (isRecording) 260.dp else 220.dp,
        animationSpec = tween(durationMillis = 300),
        label = "buttonSize"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top-left logo: gradient circle + "yooni" text
        Row(
            modifier = Modifier
                .padding(start = 24.dp, top = 24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        spotColor = Color.Black,
                        ambientColor = Color.Black
                    )
                    .clip(CircleShape)
                    .background(logoGradient)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "yooni",
                fontSize = 22.sp,
                color = YooniTextDark,
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Center: large gradient circle (record button) with shadow
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        if (!isRecording) {
                            scaleX = scale
                            scaleY = scale
                        }
                    }
                    .size(buttonSize)
                    .shadow(
                        elevation = 24.dp,
                        shape = CircleShape,
                        spotColor = Color.Black,
                        ambientColor = Color.Black
                    )
                    .clip(CircleShape)
                    .background(logoGradient)
                    .clickable {
                        if (!isRecording) {
                            isRecording = true
                            statusText = "Listening..."
                            transcriptionText = ""
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
                                        statusText = "Formatting..."
                                        actionFormatter.format(userText)
                                    } else {
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
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "",
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
        }

        // Bottom: status, actions, transcription, card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusText,
                fontSize = 16.sp,
                color = YooniTextDark.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (actionPreviewText.isNotEmpty()) {
                TextButton(
                    onClick = {
                        actionPreviewText = ""
                        statusText = "New conversation started"
                    }
                ) {
                    Text("Start Over", color = YooniTextDark)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (transcriptionText.isNotEmpty()) {
                Text(
                    text = "You said:",
                    fontSize = 14.sp,
                    color = YooniTextDark.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = transcriptionText,
                    fontSize = 18.sp,
                    color = YooniTextDark
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (actionPreviewText.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = YooniPink.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Yooni will:",
                            fontSize = 14.sp,
                            color = YooniTextDark.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = actionPreviewText,
                            fontSize = 18.sp,
                            color = YooniTextDark
                        )
                    }
                }
            }
        }
    }
}
