package com.project.smartcoastercompanion

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import java.util.Calendar

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val alarmScheduler = AlarmScheduler(application)
    private val sharedPreferences = application.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    private var requestCodeCounter = sharedPreferences.getInt("requestCodeCounter", 0)
    private val gson = Gson()

    private val _activeAlarms = MutableLiveData<List<AlarmData>>()
    val activeAlarms: LiveData<List<AlarmData>> = _activeAlarms

    init {
        loadAlarms()
        observeAlarmCompletion()
    }

    private fun observeAlarmCompletion() {
        val context = getApplication<Application>().applicationContext
        val filter = IntentFilter("com.project.smartcoastercompanion.ALARM_COMPLETED")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val requestCode = intent.getIntExtra("requestCode", 0)
                val alarmType = intent.getStringExtra("alarm_type")
                val alarmData = _activeAlarms.value?.find { it.requestCode == requestCode }

                alarmData?.let { alarm ->
                    when (alarmType) {
                        "regular" -> {
                            // Remove regular alarm after it's completed
                            removeAlarm(alarm)
                        }
                        "interval" -> {
                            // Update the next trigger time for interval alarms
                            val intervalMillis = intent.getLongExtra("intervalMillis", 0L)
                            val updatedAlarm = alarm.copy(
                                timeInMillis = System.currentTimeMillis() + intervalMillis
                            )
                            updateAlarm(updatedAlarm)
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
    }

    private fun updateAlarm(alarmData: AlarmData) {
        val currentAlarms = _activeAlarms.value?.toMutableList() ?: mutableListOf()
        val index = currentAlarms.indexOfFirst { it.requestCode == alarmData.requestCode }
        if (index != -1) {
            currentAlarms[index] = alarmData
            _activeAlarms.value = currentAlarms
            saveAlarm(alarmData)
        }
    }

    private fun saveAlarm(alarmData: AlarmData) {
        val alarmJson = gson.toJson(alarmData)
        sharedPreferences.edit().putString(alarmData.requestCode.toString(), alarmJson).apply()
    }

    // Load all alarms from SharedPreferences into activeAlarms LiveData
    private fun loadAlarms() {
        val savedAlarms = mutableListOf<AlarmData>()
        sharedPreferences.all.forEach { (key, value) ->
            try {
                val alarmJson = value as String
                val alarmData = gson.fromJson(alarmJson, AlarmData::class.java)
                savedAlarms.add(alarmData)
                requestCodeCounter = maxOf(requestCodeCounter, alarmData.requestCode)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _activeAlarms.value = savedAlarms
    }

    fun setRegularAlarm(calendar: Calendar, alarmName: String) {
        val timeInMillis = calendar.timeInMillis
        requestCodeCounter++
        saveRequestCodeCounter()
        alarmScheduler.setRegularAlarm(timeInMillis, requestCodeCounter)
        val alarmData = AlarmData(alarmName, timeInMillis, "regular", requestCodeCounter)
        addAlarm(alarmData)
        saveAlarm(alarmData)
    }

    fun setIntervalAlarm(intervalMillis: Long, alarmName: String) {
        if (_activeAlarms.value?.any { it.type == "interval" } == true) {
            Toast.makeText(getApplication(), "Active interval alarm already set", Toast.LENGTH_SHORT).show()
            return
        }

        requestCodeCounter++
        saveRequestCodeCounter()
        alarmScheduler.setIntervalAlarm(intervalMillis, requestCodeCounter)
        val alarmData = AlarmData(alarmName, System.currentTimeMillis() + intervalMillis, "interval", requestCodeCounter)
        addAlarm(alarmData)
        saveAlarm(alarmData)
    }

    fun cancelAllAlarms() {
        _activeAlarms.value?.forEach { alarm ->
            alarmScheduler.cancelAlarm(alarm.requestCode)
        }
        _activeAlarms.value = emptyList()
        sharedPreferences.edit().clear().apply()
    }

    fun cancelAlarm(alarmData: AlarmData) {
        alarmScheduler.cancelAlarm(alarmData.requestCode)
        removeAlarm(alarmData)
    }

    private fun addAlarm(alarmData: AlarmData) {
        val currentAlarms = _activeAlarms.value?.toMutableList() ?: mutableListOf()
        currentAlarms.add(alarmData)
        _activeAlarms.value = currentAlarms
    }

    private fun removeAlarm(alarmData: AlarmData) {
        val currentAlarms = _activeAlarms.value?.toMutableList() ?: mutableListOf()
        currentAlarms.removeAll { it.requestCode == alarmData.requestCode }
        _activeAlarms.value = currentAlarms
        sharedPreferences.edit().remove(alarmData.requestCode.toString()).apply()
    }


    private fun saveRequestCodeCounter() {
        sharedPreferences.edit().putInt("requestCodeCounter", requestCodeCounter).apply()
    }

    data class AlarmData(
        val name: String,
        val timeInMillis: Long,
        val type: String, // "regular" or "interval"
        val requestCode: Int, // Unique request code for cancellation
        val intervalMillis: Long? = null //display interval title
    )
}
