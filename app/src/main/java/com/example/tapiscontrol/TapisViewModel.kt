package com.example.tapiscontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class TapisViewModel(app: Application) : AndroidViewModel(app) {

    val ble = TreadmillBle(app)
    val runner = WorkoutRunner(ble, viewModelScope)
    private val store = WorkoutStore(app)

    val steps = MutableStateFlow(store.loadSteps())
    val maxSpeed = MutableStateFlow(store.loadMaxSpeed())
    val manualSpeed = MutableStateFlow(3.0)
    val manualIncline = MutableStateFlow(0.0)

    // ---- editor allenamento
    fun addStep() = steps.update {
        it + (it.lastOrNull()?.copy(id = newStepId())
            ?: Step(durationSec = 300, speedKmh = 4.0, inclinePct = 0.0))
    }.also { store.saveSteps(steps.value) }

    fun updateStep(step: Step) {
        steps.update { l -> l.map { if (it.id == step.id) step else it } }
        store.saveSteps(steps.value)
    }

    fun removeStep(id: Long) {
        steps.update { l -> l.filterNot { it.id == id } }
        store.saveSteps(steps.value)
    }

    fun setMaxSpeed(v: Double) {
        val c = v.coerceIn(3.0, 15.0)
        maxSpeed.value = c
        store.saveMaxSpeed(c)
    }

    // ---- controllo manuale
    fun manualStart() {
        viewModelScope.launch {
            ble.requestControl()
            ble.start()
            delay(4000)
            ble.setSpeed(manualSpeed.value)
            ble.setIncline(manualIncline.value)
        }
    }

    fun manualStop() = ble.stop()

    fun adjustSpeed(delta: Double) {
        val v = ((manualSpeed.value + delta) * 10).roundToInt() / 10.0
        manualSpeed.value = v.coerceIn(1.0, maxSpeed.value)
        ble.setSpeed(manualSpeed.value)
    }

    fun adjustIncline(delta: Double) {
        manualIncline.value = (manualIncline.value + delta).coerceIn(0.0, 15.0)
        ble.setIncline(manualIncline.value)
    }

    override fun onCleared() {
        ble.disconnect()
    }
}
