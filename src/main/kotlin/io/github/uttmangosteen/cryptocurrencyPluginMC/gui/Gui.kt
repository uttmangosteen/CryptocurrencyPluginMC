package io.github.uttmangosteen.cryptocurrencyPluginMC.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

abstract class Gui(rows: Int, val title: Component) : InventoryHolder {
    val size: Int = rows.coerceIn(1, 6) * 9
    private val inventory: Inventory = Bukkit.createInventory(this, size, title)
    private val actions = mutableMapOf<Int, (InventoryClickEvent) -> Unit>()

    open val cancelClicks: Boolean = true

    var parentGuiOpener: (() -> Unit)? = null

    override fun getInventory(): Inventory = inventory

    fun setItem(slot: Int, item: ItemStack, action: ((InventoryClickEvent) -> Unit)? = null) {
        inventory.setItem(slot, item)
        if (action != null) {
            actions[slot] = action
        } else {
            actions.remove(slot)
        }
    }

    fun open(player: Player) {
        player.openInventory(inventory)
    }

    open fun onClick(event: InventoryClickEvent) {
        actions[event.slot]?.invoke(event)
    }

    open fun onClose(e: InventoryCloseEvent): Boolean {
        return false
    }
}