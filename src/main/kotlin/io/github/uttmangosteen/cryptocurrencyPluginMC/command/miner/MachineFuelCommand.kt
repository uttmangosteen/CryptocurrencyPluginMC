package io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.Material
import org.bukkit.entity.Player

class MachineFuelCommand(
    private val plugin: Main
) {
    private val prefix = plugin.pluginConfig.prefix

    //TODO:燃料システムの再考

    fun execute(player: Player, args: Array<out String>) {
        val machineId = args.getOrNull(1) ?: return
        fuel(player, machineId)
    }

    private fun fuel(player: Player, machineId: String) {
        val item = player.inventory.itemInMainHand
        val fuelPerItem = when (item.type) {
            Material.COBBLESTONE -> 1
            else -> 0
        }

        if (fuelPerItem <= 0 || item.amount <= 0) {
            player.sendMessage("$prefix§c丸石を手に持ってください")
            return
        }

        val amount = item.amount
        val fuelAmount = fuelPerItem * amount

        plugin.launchAsync {
            val updated = plugin.miningMachineService?.modifyMachineExternal(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString(),
                requireOwner = false
            ) { machine ->
                machine.addFuel(fuelAmount)
            } ?: false

            plugin.runSync {
                if (updated) {
                    item.amount = 0
                    player.sendMessage("$prefix§a燃料を §f$fuelAmount §a追加しました")
                } else {
                    player.sendMessage("$prefix§c燃料の追加に失敗しました")
                }
            }
        }
    }
}