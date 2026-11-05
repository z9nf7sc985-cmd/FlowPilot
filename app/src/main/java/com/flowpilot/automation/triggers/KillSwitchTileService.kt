package com.flowpilot.automation.triggers

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.flowpilot.automation.engine.RunLog
import com.flowpilot.automation.service.AutomationService

class KillSwitchTileService : TileService() {
    override fun onStartListening() {
        qsTile?.state = if (RunLog.runningAutomationId.value != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }

    override fun onClick() {
        RunLog.globalKill = true
        AutomationService.stop(this)
        qsTile?.state = Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }
}
