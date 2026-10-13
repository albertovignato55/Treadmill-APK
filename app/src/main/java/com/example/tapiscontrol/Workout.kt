package com.example.tapiscontrol

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val idCounter = AtomicLong()
fun newStepId(): Long = idCounter.incrementAndGet()

data class Step(
    val id: Long = newStepId(),
    val durationSec: Int,
    val speedKmh: Double,
    val inclinePct: Double,
)

/** Salvataggio locale dell'allenamento e del limite di velocita'. */
class WorkoutStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("workouts", Context.MODE_PRIVATE)

    fun loadSteps(): List<Step> {
        val raw = prefs.getString("steps", null) ?: return defaultSteps()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Step(durationSec = o.getInt("d"), speedKmh = o.getDouble("s"), inclinePct = o.getDouble("i"))
            }
        } catch (e: Exception) {
            defaultSteps()
        }
    }

    fun saveSteps(steps: List<Step>) {
        val arr = JSONArray()
        steps.forEach {
            arr.put(JSONObject().put("d", it.durationSec).put("s", it.speedKmh).put("i", it.inclinePct))
        }
        prefs.edit().putString("steps", arr.toString()).apply()
    }

    fun loadMaxSpeed(): Double = prefs.getFloat("max", 8f).toDouble()
    fun saveMaxSpeed(v: Double) = prefs.edit().putFloat("max", v.toFloat()).apply()

    private fun defaultSteps() = listOf(
        Step(durationSec = 300, speedKmh = 4.0, inclinePct = 0.0),
        Step(durationSec = 180, speedKmh = 5.5, inclinePct = 2.0),
        Step(durationSec = 120, speedKmh = 4.5, inclinePct = 0.0),
    )
}

private const val START_DELAY_MS = 4000L     // attesa dopo "Start" (molti tapis fanno un conto alla rovescia)
private const val COOLDOWN_SPEED = 2.0

/** Esegue in automatico la lista di step, con rampe graduali tra uno step e l'altro. */
class WorkoutRunner(private val ble: TreadmillBle, private val scope: CoroutineScope) {

    data class Progress(
        val running: Boolean = false,
        val paused: Boolean = false,
        val stepIndex: Int = -1,
        val stepCount: Int = 0,
        val stepRemainingSec: Int = 0,
        val totalRemainingSec: Int = 0,
        val message: String = "",
    )

    val progress = MutableStateFlow(Progress())

    /** Durata (s) della rampa all'inizio di ogni step (tranne il primo). */
    var rampSec = 6

    private var job: Job? = null
    @Volatile private var paused = false

    private fun round1(x: Double) = (x * 10).roundToInt() / 10.0

    fun start(steps: List<Step>, maxSpeed: Double) {
        if (steps.isEmpty() || job?.isActive == true) return
        paused = false
        job = scope.launch { run(steps, maxSpeed) }
    }

    fun pauseResume() {
        if (job?.isActive != true) return
        paused = !paused
        progress.update { it.copy(paused = paused) }
        if (paused) ble.pause()
    }

    fun stop() {
        val wasRunning = progress.value.running
        job?.cancel()
        job = null
        paused = false
        if (wasRunning) ble.stop()
        progress.value = Progress(message = "Fermato")
    }

    private suspend fun run(steps: List<Step>, maxSpeed: Double) {
        val total = steps.sumOf { it.durationSec }
        var elapsedTotal = 0
        progress.value = Progress(
            running = true, stepCount = steps.size, totalRemainingSec = total, message = "Avvio...",
        )

        ble.requestControl()
        ble.start()
        delay(START_DELAY_MS)

        var prevSpeed = round1(min(steps[0].speedKmh, maxSpeed))
        var prevInc = steps[0].inclinePct.roundToInt().toDouble()
        var sentSpeed: Double? = null
        var sentInc: Double? = null

        for ((idx, step) in steps.withIndex()) {
            val tgtSpeed = round1(min(step.speedKmh, maxSpeed))
            val tgtInc = step.inclinePct.roundToInt().toDouble()

            for (t in 0 until step.durationSec) {
                if (!ble.connected.value) {
                    progress.value = Progress(
                        message = "Connessione persa! Il tapis roulant mantiene l'ultima velocita': " +
                            "fermalo con la chiave di sicurezza.",
                    )
                    return
                }
                if (paused) {
                    while (paused) delay(250)
                    ble.start()
                    delay(START_DELAY_MS)
                    sentSpeed = null
                    sentInc = null
                }

                val f = if (idx == 0 || rampSec <= 0) 1.0
                else ((t + 1).toDouble() / rampSec).coerceAtMost(1.0)
                val sp = round1(prevSpeed + (tgtSpeed - prevSpeed) * f)
                val inc = (prevInc + (tgtInc - prevInc) * f).roundToInt().toDouble()

                if (sp != sentSpeed) { ble.setSpeed(sp); sentSpeed = sp }
                if (inc != sentInc) { ble.setIncline(inc); sentInc = inc }

                progress.value = Progress(
                    running = true,
                    stepIndex = idx,
                    stepCount = steps.size,
                    stepRemainingSec = step.durationSec - t,
                    totalRemainingSec = total - elapsedTotal,
                )
                delay(1000)
                elapsedTotal++
            }
            prevSpeed = tgtSpeed
            prevInc = tgtInc
        }

        // Defaticamento graduale, poi stop
        progress.value = Progress(running = true, stepIndex = steps.size - 1, stepCount = steps.size,
            message = "Defaticamento...")
        ble.setIncline(0.0)
        var s = prevSpeed
        while (s > COOLDOWN_SPEED) {
            s = max(COOLDOWN_SPEED, s - 1.0)
            ble.setSpeed(s)
            delay(3000)
        }
        delay(3000)
        ble.stop()
        progress.value = Progress(message = "Allenamento completato")
    }
}
