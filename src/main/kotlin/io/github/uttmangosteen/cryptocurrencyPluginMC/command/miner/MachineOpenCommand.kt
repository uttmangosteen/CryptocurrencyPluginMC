package io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.gui.miningmachine.MiningMachineGui
import org.bukkit.entity.Player

class MachineOpenCommand(
    private val plugin: Main
) {
    private val prefix = plugin.pluginConfig.prefix

    fun execute(player: Player, args: Array<out String>) {
        val machineId = args.getOrNull(1) ?: return

        plugin.launchAsync {
            val machine = plugin.miningMachineService?.getMachine(machineId)

            plugin.runSync {
                if (machine == null) {
                    player.sendMessage("$prefix§c指定された採掘機が見つかりません")
                    return@runSync
                }
                if (!machine.canAccess(player.uniqueId.toString()) &&
                    !player.hasPermission("cryptocurrency.admin")
                ) {
                    player.sendMessage("$prefix§c権限がありません")
                    return@runSync
                }

                MiningMachineGui(plugin, machine).open(player)
            }
        }
    }
}