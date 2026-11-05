package com.calmdrive.visualtimer.engine

import android.content.Context
import com.calmdrive.visualtimer.data.PreferencesRepository
import com.calmdrive.visualtimer.model.DEFAULT_ROUTINES
import com.calmdrive.visualtimer.model.NumberMode
import com.calmdrive.visualtimer.model.Routine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

class TimerEngine(private val context: Context) {

    private val prefs = PreferencesRepository(context)
    private val spokenEngine = SpokenAlertEngine(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    private var tickerJob: Job? = null

    var activeRoutine: Routine = prefs.getRoutines().find { it.id == prefs.activeRoutineId }
        ?: DEFAULT_ROUTINES.first()
        private set

    var isFirstStep = true
        private set

    var totalSec: Int = activeRoutine.firstMins * 60
        private set

    var remainingSec: Int = totalSec
        private set

    var isPaused = true
        private set

    private val firedMilestones = mutableSetOf<String>()

    var onStateChanged: ((RenderState) -> Unit)? = null

    init {
        startRoutine(activeRoutine, announce = false, autoStart = false)
    }

    fun startRoutine(routine: Routine, announce: Boolean = true, autoStart: Boolean = false) {
        activeRoutine = routine
        prefs.activeRoutineId = routine.id
        isFirstStep = true
        totalSec = routine.firstMins * 60
        remainingSec = totalSec
        isPaused = !autoStart
        firedMilestones.clear()

        if (autoStart) {
            restartTicker()
        } else {
            tickerJob?.cancel()
        }
        notifyState()

        if (announce) {
            if (prefs.chimesEnabled) {
                HarmonicChimePlayer.playChime(isDone = false)
            }
            if (prefs.voiceEnabled) {
                val thenPart = if (!routine.thenTitle.isNullOrEmpty()) {
                    ". Then: ${routine.thenTitle}."
                } else ""
                val prompt = if (autoStart) "Starting timer" else "Timer ready"
                spokenEngine.speak("$prompt: ${routine.firstMins} minutes for ${routine.firstTitle}$thenPart")
            }
        }
    }

    fun resetTimer() {
        isFirstStep = true
        totalSec = activeRoutine.firstMins * 60
        remainingSec = totalSec
        isPaused = true
        firedMilestones.clear()
        tickerJob?.cancel()
        notifyState()
    }

    fun startQuickTimer(mins: Int, autoStart: Boolean = false) {
        val quick = Routine(
            id = "quick-$mins",
            firstTitle = "$mins Minute Timer",
            firstIcon = "⏱️",
            firstMins = mins,
            thenTitle = "All Done!",
            thenIcon = "🌟",
            thenMins = 0
        )
        startRoutine(quick, announce = true, autoStart = autoStart)
    }

    fun togglePlayPause(): Boolean {
        isPaused = !isPaused
        if (!isPaused) {
            restartTicker()
        } else {
            tickerJob?.cancel()
        }
        notifyState()
        return isPaused
    }

    fun adjustTime(deltaSeconds: Int) {
        remainingSec = (remainingSec + deltaSeconds).coerceAtLeast(0)
        if (remainingSec > totalSec) {
            totalSec = remainingSec
        }
        notifyState()
    }

    fun skipToThen() {
        if (!activeRoutine.thenTitle.isNullOrEmpty() && activeRoutine.thenMins > 0) {
            isFirstStep = false
            totalSec = activeRoutine.thenMins * 60
            remainingSec = totalSec
            isPaused = false
            firedMilestones.clear()

            if (prefs.chimesEnabled) HarmonicChimePlayer.playChime(isDone = true)
            if (prefs.voiceEnabled) {
                spokenEngine.speak("First step complete! Now: ${activeRoutine.thenTitle}, for ${activeRoutine.thenMins} minutes.")
            }
            restartTicker()
            notifyState()
        } else {
            remainingSec = 0
            notifyState()
        }
    }

    fun updateMapsEta(mapsEtaSec: Int) {
        if (!prefs.mapsSyncEnabled || !isFirstStep) return
        if (mapsEtaSec <= 0) return

        remainingSec = mapsEtaSec
        if (remainingSec > totalSec) {
            totalSec = remainingSec
        }
        notifyState()
    }

    private fun restartTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (!isPaused && remainingSec > 0) {
                    remainingSec -= 1
                    checkMilestones()
                    notifyState()
                }
            }
        }
    }

    private fun checkMilestones() {
        val halfSec = totalSec / 2

        // Halfway
        if (totalSec >= 120 && remainingSec == halfSec && !firedMilestones.contains("halfway")) {
            firedMilestones.add("halfway")
            triggerMilestone(
                "⭐ Halfway There!",
                "Halfway to ${getCurrentTitle()}",
                isDone = false
            )
        }

        // 5 min (300s), 2 min (120s), 1 min (60s)
        listOf(300, 120, 60).forEach { mSec ->
            val key = mSec.toString()
            if (totalSec > mSec + 15 && remainingSec == mSec && !firedMilestones.contains(key)) {
                firedMilestones.add(key)
                val mins = mSec / 60
                val label = if (mins == 1) "1 minute left" else "$mins minutes left"
                val thenHint = if (isFirstStep && !activeRoutine.thenTitle.isNullOrEmpty()) {
                    ". Then: ${activeRoutine.thenTitle}"
                } else ""
                triggerMilestone(
                    "🔔 $label",
                    "Almost at ${getCurrentTitle()}$thenHint",
                    isDone = false
                )
            }
        }

        // Completion (0s)
        if (remainingSec <= 0 && !firedMilestones.contains("0")) {
            firedMilestones.add("0")
            if (isFirstStep && !activeRoutine.thenTitle.isNullOrEmpty() && activeRoutine.thenMins > 0) {
                triggerMilestone(
                    "🎉 Arrived: ${activeRoutine.firstTitle}!",
                    "Up next: ${activeRoutine.thenIcon ?: ""} ${activeRoutine.thenTitle}",
                    isDone = true
                )
                scope.launch {
                    delay(3000)
                    if (remainingSec <= 0 && isFirstStep) {
                        skipToThen()
                    }
                }
            } else {
                triggerMilestone(
                    "🎉 All Done: ${getCurrentTitle()}!",
                    "Great job completing the routine!",
                    isDone = true
                )
            }
        }
    }

    private fun triggerMilestone(title: String, speech: String, isDone: Boolean) {
        if (prefs.chimesEnabled) {
            HarmonicChimePlayer.playChime(isDone = isDone)
        }
        if (prefs.voiceEnabled) {
            spokenEngine.speak(speech)
        }
    }

    fun getCurrentTitle(): String =
        if (isFirstStep) activeRoutine.firstTitle else (activeRoutine.thenTitle ?: activeRoutine.firstTitle)

    fun getCurrentIcon(): String =
        if (isFirstStep) activeRoutine.firstIcon else (activeRoutine.thenIcon ?: "✨")

    fun formatTimeForKid(sec: Int): String {
        if (sec <= 0) return "Done!"
        return when (prefs.numberMode) {
            NumberMode.EXACT -> {
                val m = sec / 60
                val rem = sec % 60
                String.format("%d:%02d", m, rem)
            }
            NumberMode.ROUNDED -> {
                if (sec > 60) {
                    val m = ceil(sec / 60.0).toInt()
                    "$m min"
                } else if (sec > 10) {
                    val ten = ceil(sec / 10.0).toInt() * 10
                    "${ten}s"
                } else {
                    "${sec}s"
                }
            }
            NumberMode.NONE -> ""
        }
    }

    fun buildCurrentState(): RenderState {
        return RenderState(
            totalSec = totalSec,
            remainingSec = remainingSec,
            isPaused = isPaused,
            visualStyle = prefs.visualStyle,
            numberMode = prefs.numberMode,
            paletteTheme = prefs.paletteTheme,
            isFirstStep = isFirstStep,
            currentTitle = getCurrentTitle(),
            currentIcon = getCurrentIcon(),
            formattedTime = formatTimeForKid(remainingSec),
            thenTitle = activeRoutine.thenTitle,
            thenIcon = activeRoutine.thenIcon
        )
    }

    fun notifyState() {
        onStateChanged?.invoke(buildCurrentState())
    }

    fun release() {
        tickerJob?.cancel()
        spokenEngine.release()
    }
}
