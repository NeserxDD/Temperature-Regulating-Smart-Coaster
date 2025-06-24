package com.project.smartcoastercompanion

import androidx.compose.runtime.mutableFloatStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class TemperatureViewModel : ViewModel() {
    var currentTemperature: Float by mutableFloatStateOf(0f)
    var preferredTemperature: Float by mutableFloatStateOf(40f)
    var threshold: Float by mutableFloatStateOf(0f)  // Only store threshold, no need for min/max

    private var outputStream: OutputStream? = null

    // Set Bluetooth output stream
    fun setBluetoothOutputStream(bluetoothOutputStream: OutputStream) {
        outputStream = bluetoothOutputStream
    }

    // Send the preferred temperature and threshold to Arduino
    fun updatePreferredTemperature(value: Float) {
        preferredTemperature = value
        sendCommandToArduino("N1$value")  // Send preferred temperature
    }

    fun updateThreshold(value: Float) {
        threshold = value
        sendCommandToArduino("N2${threshold}")  // Send max temp
        sendCommandToArduino("N3${threshold}")  // Send min temp
    }

    // Send commands over Bluetooth
    private fun sendCommandToArduino(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                outputStream?.write("$command\n".toByteArray())
                outputStream?.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun updateTemperature(newTemperature: Float) {
        currentTemperature = newTemperature
    }
}
