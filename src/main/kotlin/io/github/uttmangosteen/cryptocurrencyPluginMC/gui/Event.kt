package io.github.uttmangosteen.cryptocurrencyPluginMC.gui

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.plugin.java.JavaPlugin

class Event : Listener {
    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val view = e.view
        val topInventory = view.topInventory
        val holder = topInventory.holder
        if (holder !is Gui) return
        if (holder.cancelClicks) e.isCancelled = true
        val clickedInventory = e.clickedInventory ?: return
        if (clickedInventory == topInventory) holder.onClick(e)
    }

    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        val holder = e.inventory.holder
        if (holder is Gui) {
            val cancelPop = holder.onClose(e)
            val parentOpener = holder.parentGuiOpener
            if (!cancelPop && parentOpener != null) {
                val plugin = JavaPlugin.getPlugin(Main::class.java)
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    parentOpener.invoke()
                }, 1L)
            }
        }
    }
}