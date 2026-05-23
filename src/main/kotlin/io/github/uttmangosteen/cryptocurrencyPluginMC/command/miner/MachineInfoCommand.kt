package io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.MiningMachine
import org.bukkit.entity.Player

class MachineInfoCommand(
    private val plugin: Main
) {
    private val prefix = plugin.pluginConfig.prefix

    fun execute(player: Player, args: Array<out String>) {
        val machineId = args.getOrNull(1) ?: return
        info(player, machineId)
    }

    private fun info(player: Player, machineId: String) {
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

                sendMachineInfo(player, machine)
            }
        }
    }

    private fun sendMachineInfo(player: Player, machine: MiningMachine) {
        player.sendMessage("$prefix§f§l========== §8§lMining Machine §f§l==========")
        player.sendMessage("$prefix§7id: §f${machine.id}")
        player.sendMessage("$prefix§7owner: §f${machine.ownerUuid}")
        player.sendMessage("$prefix§7enabled: §f${machine.enabled}")
        player.sendMessage("$prefix§7status: §f${machine.status}")
        player.sendMessage("$prefix§7fuel: §f${machine.fuelAmount}")
        player.sendMessage("$prefix§7gpuPower: §f${machine.totalGpuPower()}")

        player.sendMessage("$prefix§7GPU Status:")
        machine.gpuSlots.forEachIndexed { index, gpu ->
            if (gpu == null) {
                player.sendMessage("$prefix§8[$index] -")
            } else {
                val (color, statusText) = when {
                    gpu.life > 0 -> "§a" to "Active"
                    gpu.life == 0 -> "§e" to "EndOfLife"
                    else -> "§c" to "Broken"
                }
                player.sendMessage("$prefix§7[$index] §f${gpu.gpuName} §7Power: §a${gpu.power} §7Life: $color${gpu.life} §7($statusText)")
            }
        }

        val miningBlock = machine.miningBlock
        if (miningBlock != null) {
            player.sendMessage("$prefix§7Block Info:")
            player.sendMessage("$prefix §7- Height: §f${miningBlock.height}")
            player.sendMessage("$prefix §7- TX Count: §f${miningBlock.transactions.size}")
        }

        player.sendMessage("$prefix§7txMode: §f${machine.createBlockMode}")
        player.sendMessage("$prefix§7shareName: §f${machine.shareNameOnMined}")
        player.sendMessage("$prefix§7memo: §f${machine.memo}")
        player.sendMessage("$prefix§7defaultMemo: §f${machine.defaultMemo}")
        player.sendMessage("$prefix§7rewardAccount: §8${machine.rewardAccountPubKey ?: "none"}")
        player.sendMessage("$prefix§f§l====================================")
    }
}