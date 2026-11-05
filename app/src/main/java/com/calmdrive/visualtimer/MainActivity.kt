package com.calmdrive.visualtimer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.calmdrive.visualtimer.data.PreferencesRepository
import com.calmdrive.visualtimer.engine.HarmonicChimePlayer
import com.calmdrive.visualtimer.engine.RenderState
import com.calmdrive.visualtimer.engine.SpokenAlertEngine
import com.calmdrive.visualtimer.engine.TimerArtworkRenderer
import com.calmdrive.visualtimer.model.DEFAULT_ROUTINES
import com.calmdrive.visualtimer.model.NumberMode
import com.calmdrive.visualtimer.model.PaletteTheme
import com.calmdrive.visualtimer.model.Routine
import com.calmdrive.visualtimer.model.VisualStyle
import com.calmdrive.visualtimer.service.CalmDriveMediaService
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var prefs: PreferencesRepository
    private var spokenEngine: SpokenAlertEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesRepository(this)
        spokenEngine = SpokenAlertEngine(this)

        // Start media service
        val serviceIntent = Intent(this, CalmDriveMediaService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                val mediaController = MediaControllerCompat(this@MainActivity, mediaBrowser.sessionToken)
                MediaControllerCompat.setMediaController(this@MainActivity, mediaController)
            }
        }

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, CalmDriveMediaService::class.java),
            connectionCallback,
            null
        )
        mediaBrowser.connect()

        setContent {
            CalmDriveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF090B10)
                ) {
                    CompanionAppScreen(
                        prefs = prefs,
                        onTestChime = { HarmonicChimePlayer.playChime(isDone = false) },
                        onTestVoice = {
                            val activeTitle = CalmDriveMediaService.instance?.timerEngine?.getCurrentTitle() ?: "School"
                            spokenEngine?.speak("Five minutes left until $activeTitle. Great job keeping calm!")
                        },
                        onRequestNotificationAccess = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        spokenEngine?.release()
        if (mediaBrowser.isConnected) {
            mediaBrowser.disconnect()
        }
    }
}

@Composable
fun CalmDriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8AB4F8),
            background = Color(0xFF090B10),
            surface = Color(0xFF191E2B),
            onPrimary = Color(0xFF0C1018),
            onBackground = Color(0xFFF3F6FC),
            onSurface = Color(0xFFF3F6FC)
        ),
        content = content
    )
}

@Composable
fun CompanionAppScreen(
    prefs: PreferencesRepository,
    onTestChime: () -> Unit,
    onTestVoice: () -> Unit,
    onRequestNotificationAccess: () -> Unit
) {
    var renderState by remember { mutableStateOf<RenderState?>(null) }
    var currentStyle by remember { mutableStateOf(prefs.visualStyle) }
    var currentNumberMode by remember { mutableStateOf(prefs.numberMode) }
    var currentPalette by remember { mutableStateOf(prefs.paletteTheme) }
    var chimesEnabled by remember { mutableStateOf(prefs.chimesEnabled) }
    var voiceEnabled by remember { mutableStateOf(prefs.voiceEnabled) }
    var mapsSyncEnabled by remember { mutableStateOf(prefs.mapsSyncEnabled) }
    var routines by remember { mutableStateOf(prefs.getRoutines()) }
    var routineToEdit by remember { mutableStateOf<Routine?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var routineToDelete by remember { mutableStateOf<Routine?>(null) }

    // Poll engine state
    LaunchedEffect(Unit) {
        while (true) {
            val engine = CalmDriveMediaService.instance?.timerEngine
            if (engine != null) {
                renderState = engine.buildCurrentState()
            }
            delay(250)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CalmDrive Visual Timer",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Android Auto Split-Screen Companion",
                        fontSize = 12.sp,
                        color = Color(0xFFA2ADC2)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = Color(0x2034D399),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x6634D399))
                ) {
                    Text(
                        text = "🚗 Auto Ready",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF34D399)
                    )
                }
            }
        }

        // Active Live Timer Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B202D))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val bitmap: Bitmap? = renderState?.let { TimerArtworkRenderer.render(it) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Visual Timer Artwork",
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .background(Color(0xFF0F131C), RoundedCornerShape(16.dp))
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        val isFirst = renderState?.isFirstStep ?: true
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = if (isFirst) Color(0x2934D399) else Color(0x29A78BFA)
                        ) {
                            Text(
                                text = if (isFirst) "FIRST • RUNNING" else "THEN • STEP 2",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFirst) Color(0xFF34D399) else Color(0xFFA78BFA)
                            )
                        }

                        Text(
                            text = "${renderState?.currentIcon ?: "🚗"} ${renderState?.currentTitle ?: "Timer"}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = renderState?.formattedTime ?: "0:00",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        if (!renderState?.thenTitle.isNullOrEmpty() && isFirst) {
                            Text(
                                text = "Then: ${renderState?.thenIcon ?: ""} ${renderState?.thenTitle}",
                                fontSize = 11.sp,
                                color = Color(0xFF8AB4F8)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Actions
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val engine = CalmDriveMediaService.instance?.timerEngine
                            Button(
                                onClick = { engine?.adjustTime(-60) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x1FFFFFFF))
                            ) {
                                Text("-1m", fontSize = 12.sp, color = Color.White)
                            }

                            Button(
                                onClick = { engine?.togglePlayPause() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8AB4F8))
                            ) {
                                Text(
                                    if (renderState?.isPaused == true) "▶ Play" else "⏸ Pause",
                                    fontSize = 12.sp,
                                    color = Color(0xFF09101D),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = { engine?.adjustTime(60) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x1FFFFFFF))
                            ) {
                                Text("+1m", fontSize = 12.sp, color = Color.White)
                            }

                            Button(
                                onClick = { engine?.resetTimer() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x1FFFFFFF))
                            ) {
                                Text("↺ Reset", fontSize = 12.sp, color = Color(0xFFA2ADC2))
                            }
                        }
                    }
                }
            }
        }

        // Section 1: Visual Style Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF191E2B))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🎨 1. Visual Timer Graphic Style", fontWeight = FontWeight.Bold, color = Color.White)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VisualStyle.entries.forEach { style ->
                            val isSelected = currentStyle == style
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        currentStyle = style
                                        prefs.visualStyle = style
                                        CalmDriveMediaService.instance?.timerEngine?.notifyState()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0x2B8AB4F8) else Color(0x0CFFFFFF),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF8AB4F8) else Color(0x18FFFFFF)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(style.icon, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        style.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Sensory & Low-Anxiety Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF191E2B))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🧠 2. Sensory & Low-Anxiety Settings", fontWeight = FontWeight.Bold, color = Color.White)

                    // Number Format
                    Text("Countdown Number Format", fontSize = 12.sp, color = Color(0xFFA2ADC2))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NumberMode.entries.forEach { mode ->
                            val isSelected = currentNumberMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        currentNumberMode = mode
                                        prefs.numberMode = mode
                                        CalmDriveMediaService.instance?.timerEngine?.notifyState()
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF2B3346) else Color(0x0CFFFFFF)
                            ) {
                                Text(
                                    mode.label,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else Color(0xFFA2ADC2),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    // Palette Theme
                    Text("Color Progression Theme", fontSize = 12.sp, color = Color(0xFFA2ADC2))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PaletteTheme.entries.forEach { pal ->
                            val isSelected = currentPalette == pal
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        currentPalette = pal
                                        prefs.paletteTheme = pal
                                        CalmDriveMediaService.instance?.timerEngine?.notifyState()
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF2B3346) else Color(0x0CFFFFFF)
                            ) {
                                Text(
                                    pal.title,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else Color(0xFFA2ADC2),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    // Audio & TTS Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔔 Gentle Harmonic Chimes", fontSize = 13.sp, color = Color.White)
                        Switch(
                            checked = chimesEnabled,
                            onCheckedChange = {
                                chimesEnabled = it
                                prefs.chimesEnabled = it
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🗣️ Spoken Voice Warnings (TTS)", fontSize = 13.sp, color = Color.White)
                        Switch(
                            checked = voiceEnabled,
                            onCheckedChange = {
                                voiceEnabled = it
                                prefs.voiceEnabled = it
                            }
                        )
                    }

                    // Test Audio Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTestChime,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🔔 Test Chime", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onTestVoice,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🗣️ Test Voice", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section 3: Google Maps ETA Sync
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF191E2B))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("🛰️ 3. Google Maps ETA Sync", fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                "Auto-adjusts countdown if traffic slows down",
                                fontSize = 11.sp,
                                color = Color(0xFFA2ADC2)
                            )
                        }
                        Switch(
                            checked = mapsSyncEnabled,
                            onCheckedChange = {
                                mapsSyncEnabled = it
                                prefs.mapsSyncEnabled = it
                            }
                        )
                    }

                    Button(
                        onClick = onRequestNotificationAccess,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x2538BDF8))
                    ) {
                        Text("⚙️ Check / Grant Notification Access for Maps", fontSize = 12.sp, color = Color(0xFF38BDF8))
                    }
                }
            }
        }

        // Section 4: "First -> Then" Routines
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("📋 4. First → Then Car Routines", fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Tap ✏️ to edit or 🗑️ to delete", fontSize = 11.sp, color = Color(0xFFA2ADC2))
                }
                Button(
                    onClick = {
                        routineToEdit = null
                        showEditDialog = true
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x2934D399))
                ) {
                    Text("+ Custom Routine", fontSize = 11.sp, color = Color(0xFF34D399))
                }
            }
        }

        items(routines) { routine ->
            val isActive = renderState?.currentTitle == routine.firstTitle || renderState?.currentTitle == routine.thenTitle
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (isActive) Color(0x2434D399) else Color(0xFF191E2B),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isActive) Color(0xFF34D399) else Color(0x18FFFFFF)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                CalmDriveMediaService.instance?.timerEngine?.startRoutine(routine, announce = true, autoStart = false)
                            },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(routine.firstIcon, fontSize = 24.sp)
                        Column {
                            Text(
                                "${routine.firstTitle} (${routine.firstMins}m)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            val thenText = if (!routine.thenTitle.isNullOrEmpty()) {
                                "Then: ${routine.thenIcon ?: ""} ${routine.thenTitle} (${routine.thenMins}m)"
                            } else "Single timer"
                            Text(thenText, fontSize = 11.sp, color = Color(0xFFA2ADC2))
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Edit button
                        Surface(
                            shape = CircleShape,
                            color = Color(0x1AFFFFFF),
                            modifier = Modifier.clickable {
                                routineToEdit = routine
                                showEditDialog = true
                            }
                        ) {
                            Text(
                                "✏️",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                                fontSize = 12.sp
                            )
                        }

                        // Delete button
                        Surface(
                            shape = CircleShape,
                            color = Color(0x1AFB7185),
                            modifier = Modifier.clickable {
                                routineToDelete = routine
                            }
                        ) {
                            Text(
                                "🗑️",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                                fontSize = 12.sp
                            )
                        }

                        // Start / Active / Load badge
                        val engine = CalmDriveMediaService.instance?.timerEngine
                        val isPlaying = isActive && renderState?.isPaused == false
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = if (isPlaying) Color(0x3334D399) else if (isActive) Color(0x338AB4F8) else Color(0x1CFFFFFF),
                            modifier = Modifier.clickable {
                                if (isActive) {
                                    engine?.togglePlayPause()
                                } else {
                                    engine?.startRoutine(routine, announce = true, autoStart = false)
                                }
                            }
                        ) {
                            val badgeLabel = when {
                                isPlaying -> "⏸ Pause"
                                isActive -> "▶ Start"
                                else -> "Load"
                            }
                            Text(
                                text = badgeLabel,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPlaying) Color(0xFF34D399) else if (isActive) Color(0xFF8AB4F8) else Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (routineToDelete != null) {
        val toDelete = routineToDelete!!
        AlertDialog(
            onDismissRequest = { routineToDelete = null },
            title = { Text("Delete Routine?", color = Color.White) },
            text = {
                Text(
                    "Are you sure you want to remove '${toDelete.firstTitle}'?",
                    color = Color(0xFFA2ADC2)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = routines.filterNot { it.id == toDelete.id }
                        val finalRoutines = if (updated.isEmpty()) DEFAULT_ROUTINES else updated
                        routines = finalRoutines
                        prefs.saveRoutines(finalRoutines)
                        if (renderState?.currentTitle == toDelete.firstTitle) {
                            CalmDriveMediaService.instance?.timerEngine?.startRoutine(finalRoutines.first(), announce = false)
                        }
                        routineToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFB7185))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { routineToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create / Edit Routine Dialog
    if (showEditDialog) {
        RoutineEditDialog(
            routineToEdit = routineToEdit,
            onDismiss = {
                showEditDialog = false
                routineToEdit = null
            },
            onSave = { savedRoutine ->
                val index = routines.indexOfFirst { it.id == savedRoutine.id }
                val updated = if (index >= 0) {
                    routines.toMutableList().apply { set(index, savedRoutine) }
                } else {
                    listOf(savedRoutine) + routines
                }
                routines = updated
                prefs.saveRoutines(updated)
                CalmDriveMediaService.instance?.timerEngine?.startRoutine(savedRoutine, announce = true, autoStart = false)
                showEditDialog = false
                routineToEdit = null
            },
            onDelete = { idToDelete ->
                val updated = routines.filterNot { it.id == idToDelete }
                val finalRoutines = if (updated.isEmpty()) DEFAULT_ROUTINES else updated
                routines = finalRoutines
                prefs.saveRoutines(finalRoutines)
                if (renderState?.currentTitle == routineToEdit?.firstTitle) {
                    CalmDriveMediaService.instance?.timerEngine?.startRoutine(finalRoutines.first(), announce = false)
                }
                showEditDialog = false
                routineToEdit = null
            }
        )
    }
}

@Composable
fun RoutineEditDialog(
    routineToEdit: Routine?,
    onDismiss: () -> Unit,
    onSave: (Routine) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    var firstTitle by remember { mutableStateOf(routineToEdit?.firstTitle ?: "") }
    var firstIcon by remember { mutableStateOf(routineToEdit?.firstIcon ?: "🏡") }
    var firstMins by remember { mutableStateOf(routineToEdit?.firstMins?.toString() ?: "15") }
    var thenTitle by remember { mutableStateOf(routineToEdit?.thenTitle ?: "") }
    var thenIcon by remember { mutableStateOf(routineToEdit?.thenIcon ?: "🍪") }
    var thenMins by remember { mutableStateOf(routineToEdit?.thenMins?.toString() ?: "5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (routineToEdit == null) "Create First → Then Routine" else "Edit Routine",
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = firstTitle,
                    onValueChange = { firstTitle = it },
                    label = { Text("First Activity (e.g. Drive Home)") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = firstIcon,
                        onValueChange = { firstIcon = it },
                        label = { Text("Icon") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = firstMins,
                        onValueChange = { firstMins = it },
                        label = { Text("Minutes") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = thenTitle,
                    onValueChange = { thenTitle = it },
                    label = { Text("Then Activity (Optional)") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = thenIcon,
                        onValueChange = { thenIcon = it },
                        label = { Text("Icon") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = thenMins,
                        onValueChange = { thenMins = it },
                        label = { Text("Then Mins") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                if (routineToEdit != null && onDelete != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { onDelete(routineToEdit.id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFB7185)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FB7185))
                    ) {
                        Text("🗑️ Delete Routine")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (firstTitle.isNotBlank()) {
                        val routine = Routine(
                            id = routineToEdit?.id ?: "custom-${System.currentTimeMillis()}",
                            firstTitle = firstTitle.trim(),
                            firstIcon = firstIcon.trim().ifEmpty { "🚗" },
                            firstMins = firstMins.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                            thenTitle = thenTitle.trim().ifEmpty { null },
                            thenIcon = thenIcon.trim().ifEmpty { "🌟" },
                            thenMins = thenMins.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            destStreet = firstTitle.trim()
                        )
                        onSave(routine)
                    }
                }
            ) {
                Text(if (routineToEdit == null) "Save Routine" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
