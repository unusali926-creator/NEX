package com.nex.ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var isPoweredOn by mutableStateOf(false)
    private var assistantState by mutableStateOf("STANDBY")
    private var statusDetail by mutableStateOf("TAP POWER ON TO ACTIVATE")
    private var lastRecognizedText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isPoweredOn = NexCoreService.isServiceRunning

        setContent {
            NexArcReactorHUD(
                isPoweredOn = isPoweredOn,
                assistantState = assistantState,
                statusDetail = statusDetail,
                lastHeard = lastRecognizedText,
                onReactorTap = {
                    if (!isPoweredOn) {
                        startNexSentinelService()
                    }
                },
                onPowerOn = {
                    startNexSentinelService()
                },
                onPowerSleep = {
                    stopNexSentinelService()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isPoweredOn = NexCoreService.isServiceRunning
        assistantState = NexCoreService.currentAssistantState
        statusDetail = NexCoreService.currentStatusDetail
        lastRecognizedText = NexCoreService.lastHeardText

        // Connect UI to background service updates
        NexCoreService.onStateUpdateListener = { state, detail ->
            assistantState = state
            statusDetail = detail
        }
        NexCoreService.onTextHeardListener = { query ->
            lastRecognizedText = query
        }
    }

    override fun onPause() {
        super.onPause()
        // Unlink UI listener so memory stays clean, service stays running in background!
        NexCoreService.onStateUpdateListener = null
        NexCoreService.onTextHeardListener = null
    }

    private fun startNexSentinelService() {
        requestBatteryOptimizationBypass()
        isPoweredOn = true
        val serviceIntent = Intent(this, NexCoreService::class.java).apply {
            action = NexCoreService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: Exception) {}
    }

    private fun stopNexSentinelService() {
        isPoweredOn = false
        assistantState = "STANDBY"
        statusDetail = "SYSTEM IN STANDBY"
        val serviceIntent = Intent(this, NexCoreService::class.java).apply {
            action = NexCoreService.ACTION_STOP
        }
        try {
            startService(serviceIntent)
        } catch (_: Exception) {}
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationBypass() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }
}

@Composable
fun NexArcReactorHUD(
    isPoweredOn: Boolean,
    assistantState: String,
    statusDetail: String,
    lastHeard: String,
    onReactorTap: () -> Unit,
    onPowerOn: () -> Unit,
    onPowerSleep: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
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

    val infiniteTransition = rememberInfiniteTransition(label = "ArcPulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (assistantState == "LISTENING" || assistantState == "SPEAKING") 0.90f else 0.98f,
        targetValue = if (assistantState == "LISTENING" || assistantState == "SPEAKING") 1.12f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (assistantState == "LISTENING") 650 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    val activeCyan = Color(0xFF00E5FF)
    val glowingGreen = Color(0xFF00E676)
    val sleepAmber = Color(0xFFFF9100)
    val deepDark = Color(0xFF050811)

    val currentColor = when {
        !isPoweredOn -> sleepAmber
        assistantState == "LISTENING" -> glowingGreen
        assistantState == "PROCESSING" -> Color(0xFFFFD600)
        assistantState == "SPEAKING" -> activeCyan
        else -> Color(0xFF0091EA)
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
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                Text(
                    text = "N.E.X. / F.R.I.D.A.Y.",
                    color = activeCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isPoweredOn) "STATUS: 24/7 BACKGROUND SENTINEL ONLINE" else "STATUS: STANDBY",
                    color = currentColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
            }

            // Arc Reactor
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(250.dp)
                    .scale(if (isPoweredOn) pulseScale else 1f)
                    .clickable { onReactorTap() }
            ) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .rotate(if (isPoweredOn) rotationAngle else 0f)
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                listOf(currentColor, Color.Transparent, currentColor, Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )

                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .border(width = 3.dp, color = currentColor.copy(alpha = 0.5f), shape = CircleShape)
                )

                Box(
                    modifier = Modifier
                        .size(125.dp)
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

            // Real-time Text Feedback HUD
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = statusDetail,
                    color = currentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
                if (lastHeard.isNotBlank() && isPoweredOn) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "HEARD: \"$lastHeard\"",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Power Buttons
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
