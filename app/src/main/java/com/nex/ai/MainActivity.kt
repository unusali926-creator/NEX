package com.nex.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var voiceManager: VoiceManager
    private var isPoweredOn by mutableStateOf(true)
    private var assistantState by mutableStateOf("IDLE") // IDLE, LISTENING, PROCESSING, SPEAKING, SLEEP
    private var statusSubtitle by mutableStateOf("TAP REACTOR TO SPEAK")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Nex Voice Engine
        voiceManager = VoiceManager(
            context = this,
            onSpeechRecognized = { userQuery ->
                handleIncomingQuery(userQuery)
            },
            onStateUpdate = { newState ->
                assistantState = newState
                statusSubtitle = when (newState) {
                    "LISTENING" -> "LISTENING TO COMMAND..."
                    "PROCESSING" -> "ANALYZING PROTOCOLS..."
                    "SPEAKING" -> "NEX IS RESPONDING..."
                    else -> if (isPoweredOn) "TAP REACTOR TO SPEAK" else "SYSTEM IN SLEEP MODE"
                }
            }
        )

        setContent {
            NexArcReactorHUD(
                isPoweredOn = isPoweredOn,
                assistantState = assistantState,
                subtitle = statusSubtitle,
                onReactorTap = {
                    if (isPoweredOn) {
                        voiceManager.startListening()
                    }
                },
                onPowerOn = {
                    isPoweredOn = true
                    assistantState = "IDLE"
                    statusSubtitle = "TAP REACTOR TO SPEAK"
                    voiceManager.speak("Nex online, systems fully operational. How may I assist you, Sir?")
                },
                onPowerSleep = {
                    isPoweredOn = false
                    assistantState = "SLEEP"
                    statusSubtitle = "SYSTEM IN SLEEP MODE"
                    voiceManager.stopListening()
                    voiceManager.speak("Systems powering down into standby. Good day, Sir.")
                }
            )
        }
    }

    private fun handleIncomingQuery(query: String) {
        lifecycleScope.launch {
            // First check hardware actions (Torch, Battery, Time, Apps, Sentinel)
            when (val result = HardwareManager.processVoiceCommand(this@MainActivity, query)) {
                is CommandResult.Handled -> {
                    voiceManager.speak(result.response)
                }
                is CommandResult.PassToAI -> {
                    // Send to Groq LLaMA ultra-fast engine
                    val aiResponse = AiEngine.queryNex(result.originalQuery)
                    voiceManager.speak(aiResponse)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.shutdown()
    }
}

@Composable
fun NexArcReactorHUD(
    isPoweredOn: Boolean,
    assistantState: String,
    subtitle: String,
    onReactorTap: () -> Unit,
    onPowerOn: () -> Unit,
    onPowerSleep: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Request permissions automatically on start
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needsPermission = permissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needsPermission) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // Arc Reactor Animation Transitions
    val infiniteTransition = rememberInfiniteTransition(label = "ArcPulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (assistantState == "LISTENING" || assistantState == "SPEAKING") 0.92f else 0.98f,
        targetValue = if (assistantState == "LISTENING" || assistantState == "SPEAKING") 1.10f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (assistantState == "LISTENING") 700 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    // Sci-Fi Colors
    val activeCyan = Color(0xFF00E5FF)
    val glowingBlue = Color(0xFF0091EA)
    val sleepAmber = Color(0xFFFF9100)
    val deepDark = Color(0xFF050811)

    val currentColor = when {
        !isPoweredOn -> sleepAmber
        assistantState == "LISTENING" -> Color(0xFF00E676) // Electric Green on listening
        assistantState == "PROCESSING" -> Color(0xFFFFD600) // Gold on thinking
        assistantState == "SPEAKING" -> activeCyan // Vibrant Cyan on speaking
        else -> glowingBlue
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(deepDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top HUD Bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                Text(
                    text = "N.E.X. INTELLIGENCE",
                    color = activeCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isPoweredOn) "STATUS: ONLINE (STARK CADENCE)" else "STATUS: STANDBY",
                    color = if (isPoweredOn) Color(0xFF80D8FF) else sleepAmber,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            }

            // Center ARC REACTOR
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(260.dp)
                    .scale(if (isPoweredOn) pulseScale else 1f)
                    .clickable(enabled = isPoweredOn) { onReactorTap() }
            ) {
                // Outer Rotating Tech Ring
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .rotate(if (isPoweredOn) rotationAngle else 0f)
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                listOf(currentColor, Color.Transparent, currentColor, Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )

                // Middle Conduit Ring
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .border(width = 3.dp, color = currentColor.copy(alpha = 0.5f), shape = CircleShape)
                )

                // Inner Core
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    currentColor.copy(alpha = 0.9f),
                                    currentColor.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(width = 2.dp, color = currentColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NEX",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 3.sp
                    )
                }
            }

            // Status Subtitle (HUD Feedback)
            Text(
                text = subtitle,
                color = currentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )

            // Bottom Zero-Crash Power Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onPowerOn,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006494)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .height(48.dp)
                ) {
                    Text(
                        text = "POWER ON",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                Button(
                    onClick = onPowerSleep,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                        .height(48.dp)
                ) {
                    Text(
                        text = "SLEEP",
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
