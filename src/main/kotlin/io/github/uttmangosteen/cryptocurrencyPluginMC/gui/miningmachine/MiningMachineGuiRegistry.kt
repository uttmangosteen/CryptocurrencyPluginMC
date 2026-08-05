package io.github.uttmangosteen.cryptocurrencyPluginMC.gui.miningmachine

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.Bukkit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object MiningMachineGuiRegistry {
    private val viewersByMachine = ConcurrentHashMap<String, MutableSet<UUID>>()
    private val pendingRefreshes = ConcurrentHashMap.newKeySet<String>()

    fun register(machineId: String, playerId: UUID) {
        viewersByMachine.computeIfAbsent(machineId) { ConcurrentHashMap.newKeySet<UUID>() }
            .add(playerId)
    }

    fun unregister(machineId: String, playerId: UUID) {
        viewersByMachine.computeIfPresent(machineId) { _, viewers ->
            viewers.remove(playerId)
            viewers.takeIf { it.isNotEmpty() }
        }
    }

    fun requestRefresh(plugin: Main, machineId: String) {
        if (viewersByMachine[machineId].isNullOrEmpty()) return
        if (!pendingRefreshes.add(machineId)) return

        plugin.runSync {
            try {
                refreshOpenGuis(machineId)
            } finally {
                pendingRefreshes.remove(machineId)
            }
        }
    }

    private fun refreshOpenGuis(machineId: String) {
        val viewers = viewersByMachine[machineId]?.toList() ?: return
        for (playerId in viewers) {
            val player = Bukkit.getPlayer(playerId)
            val holder = player?.openInventory?.topInventory?.holder
            if (holder is MiningMachineGui && holder.machineId == machineId) {
                holder.loadMachine()
            } else {
                unregister(machineId, playerId)
            }
        }
    }
}
