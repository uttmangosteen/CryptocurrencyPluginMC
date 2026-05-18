package io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.entity.Player

class MachineBlockCommand(
    private val plugin: Main
) {
    private val prefix = plugin.pluginConfig.prefix

    fun execute(player: Player, args: Array<out String>) {
        val action = args.getOrNull(1) ?: return
        val machineId = args.getOrNull(2) ?: return
        val memo = args.drop(3).joinToString(" ")

        when (action) {
            "setDefaultMemo" -> setDefaultMemo(player, machineId, memo)
            "setMemo" -> setMemo(player, machineId, memo)
            "txMode" -> txMode(player, machineId)
            "recreate" -> recreateBlock(player, machineId)
        }
    }

    private fun setDefaultMemo(player: Player, machineId: String, memo: String) {
        plugin.launchAsync {
            val updated = plugin.repositories.miningMachineRepo.updateMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString()
            ) { machine ->
                machine.setDefaultMemo(memo)
            }

            plugin.runSync {
                if (updated) {
                    player.sendMessage("$prefix§aデフォルトメモを設定しました")
                } else {
                    player.sendMessage("$prefix§cデフォルトメモ設定に失敗しました")
                }
            }
        }
    }

    private fun setMemo(player: Player, machineId: String, memo: String) {
        plugin.launchAsync {
            val updated = plugin.repositories.miningMachineRepo.updateMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString()
            ) { machine ->
                machine.setMiningMemo(memo)
            }

            plugin.runSync {
                if (updated) {
                    player.sendMessage("$prefix§a採掘ブロックメモを設定しました")
                } else {
                    player.sendMessage("$prefix§c採掘ブロックメモ設定に失敗しました")
                }
            }
        }
    }

    private fun txMode(player: Player, machineId: String) {
        plugin.launchAsync {
            var modeName = ""

            val updated = plugin.repositories.miningMachineRepo.updateMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString()
            ) { machine ->
                modeName = machine.toggleCreateBlockMode().name
                true
            }

            plugin.runSync {
                if (updated) {
                    player.sendMessage("$prefix§aTx収集モードを切り替えました: §f$modeName")
                } else {
                    player.sendMessage("$prefix§cTx収集モード切り替えに失敗しました")
                }
            }
        }
    }

    private fun recreateBlock(player: Player, machineId: String) {
        plugin.launchAsync {
            val updated = plugin.repositories.miningMachineRepo.updateMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString()
            ) { machine ->
                machine.setMiningBlock(null)
                true
            }

            plugin.runSync {
                if (updated) {
                    player.sendMessage("$prefix§a採掘ブロックを再構築待ちにしました")
                } else {
                    player.sendMessage("$prefix§c採掘ブロックの再構築に失敗しました")
                }
            }
        }
    }

    fun getTabCompletions(args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> listOf("setDefaultMemo", "setMemo", "txMode", "recreate").filter { it.startsWith(args[1]) }
            3 -> listOf("<machineId>").filter { it.startsWith(args[2]) }
            4 -> when (args[1]) {
                "setDefaultMemo", "setMemo" -> listOf("[memo]").filter { it.startsWith(args[3]) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}