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
            val updated = plugin.repositories.miningMachineRepo.updateMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString()
            ) { machine ->
                machine.addFuel(fuelAmount)
            }

            plugin.runSync {
                if (updated) {
                    item.amount = 0
                    player.sendMessage("$prefix§a燃料を投入しました: §f$fuelAmount")
                } else {
                    player.sendMessage("$prefix§c燃料投入に失敗しました")
                }
            }
        }
    }
}