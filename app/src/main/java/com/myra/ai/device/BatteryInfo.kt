package com.myra.ai.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object BatteryInfo {
    data class State(val percent: Int, val charging: Boolean)

    fun from(i: Intent?): State {
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        return State(if (level < 0 || scale <= 0) 0 else level * 100 / scale, plugged)
    }

    fun read(ctx: Context): State = from(ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
}
