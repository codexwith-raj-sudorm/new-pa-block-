package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.jarvis.app.widget.StarkWidgetProvider

/** Quick Settings tile: tap to toggle the "Hey Jarvis" wake service. */
class WakeTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        try {
            if (WakeService.isRunning) {
                Store(this).wakeEnabled = false
                startService(Intent(this, WakeService::class.java).setAction(WakeService.ACTION_STOP))
            } else {
                val micOk = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val overlayOk = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
                if (micOk && overlayOk) {
                    Store(this).wakeEnabled = true
                    startForegroundService(
                        Intent(this, WakeService::class.java).setAction(WakeService.ACTION_START)
                    )
                } else {
                    val i = Intent(this, MainActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivityAndCollapse(i)
                    return
                }
            }
        } catch (_: Exception) {
        }
        StarkWidgetProvider.refreshAll(this)
        refreshReactorWidgets(this)
        refresh()
    }

    private fun refresh() {
        try {
            val t = qsTile ?: return
            t.state = if (WakeService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            t.label = if (WakeService.isRunning) "Jarvis on" else "Jarvis off"
            t.updateTile()
        } catch (_: Exception) {
        }
    }
}
