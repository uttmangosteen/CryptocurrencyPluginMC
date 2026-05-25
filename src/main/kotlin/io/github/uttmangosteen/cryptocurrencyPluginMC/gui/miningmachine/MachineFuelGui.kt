package io.github.uttmangosteen.cryptocurrencyPluginMC.gui.miningmachine

import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.gui.Gui
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.event.inventory.InventoryCloseEvent

class MachineFuelGui(
    private val plugin: Main,
    private val machineId: String
) : Gui(6, LegacyComponentSerializer.legacySection().deserialize("§c§l燃料投入口")) {
    override val cancelClicks: Boolean = false

    val prefix = plugin.pluginConfig.prefix

    override fun onClose(e: InventoryCloseEvent): Boolean {
        val player = e.player
        val inv = inventory

        var totalFuel = 0
        for (i in 0 until inv.size) {
            val item = inv.getItem(i)
            if (item != null && !item.type.isAir) {
                totalFuel += item.amount
            }
        }

        inv.clear()

        if (totalFuel > 0) {
            plugin.logger.ccInfo(
                LogComponent.MINING_MACHINE_REPOSITORY,
                "Added fuel to mining machine",
                "machineId" to machineId,
                "player" to player.name,
                "amount" to totalFuel
            )
            plugin.launchAsync {
                val updated = plugin.miningMachineService?.modifyMachine(
                    machineId = machineId,
                    requesterUuid = player.uniqueId.toString(),
                    requireOwner = false,
                    bypassPermission = player.hasPermission("cryptocurrency.admin")
                ) { machine ->
                    machine.addFuel(totalFuel)
                } ?: false

                plugin.runSync {
                    if (updated) {
                        player.sendMessage("$prefix§a燃料を §f$totalFuel §a追加しました")
                    } else {
                        player.sendMessage("$prefix§c燃料の追加に失敗しました")
                    }
                }
            }
        }
        return false
    }
}