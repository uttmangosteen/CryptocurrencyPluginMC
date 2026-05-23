package io.github.uttmangosteen.cryptocurrencyPluginMC.gui

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

class Event : Listener {
    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val inventory = e.clickedInventory ?: return
        val holder = inventory.holder
        if (holder !is Gui) return

    }
}