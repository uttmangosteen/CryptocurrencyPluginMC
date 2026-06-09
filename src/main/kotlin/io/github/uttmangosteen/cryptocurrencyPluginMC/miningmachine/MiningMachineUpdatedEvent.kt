package io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class MiningMachineUpdatedEvent(
    val machineId: String,
    val actorUuid: String,
    val success: Boolean
) : Event() {
    override fun getHandlers(): HandlerList {
        return handlerList
    }

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}