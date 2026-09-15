package com.jarvis.app.hardware

import android.content.Context
import android.os.BatteryManager

class StarkDeviceController(private val context: Context) {

    fun getBatteryLevel(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

}
