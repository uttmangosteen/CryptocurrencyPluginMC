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

        when (action) {
            "setRewardPubKey" -> {
                val index = args.getOrNull(3)?.toIntOrNull() ?: 0
                setRewardPubKey(player, machineId, index)
            }

            "setMemo" -> {
                val memo = args.drop(3).joinToString(" ")
                setMemo(player, machineId, memo)
            }

            "txMode" -> txMode(player, machineId)
            "recreate" -> recreateBlock(player, machineId)
        }
    }

    private fun setRewardPubKey(player: Player, machineId: String, index: Int) {
        plugin.launchAsync {
            val account = plugin.repositories.walletRepo.getWallet(player.uniqueId.toString())
                ?.accounts?.getOrNull(index)

            if (account == null) {
                plugin.runSync {
                    player.sendMessage("$prefix§c指定されたインデックスの口座が見つかりません")
                }
                return@launchAsync
            }

            val pubKey = account.publicKey
            val updated = plugin.miningMachineService?.modifyMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString(),
                requireOwner = false,
                bypassPermission = player.hasPermission("cryptocurrency.admin")
            ) { machine ->
                machine.setRewardAccountPubKey(pubKey)
            } ?: false

            plugin.runSync {
                if (updated) {
                    player.sendMessage("$prefix§a報酬受け取り口座を設定しました: §7$pubKey")
                } else {
                    player.sendMessage("$prefix§c報酬口座の設定に失敗しました")
                }
            }
        }
    }

    private fun setMemo(player: Player, machineId: String, memo: String) {
        plugin.launchAsync {
            val updated = plugin.miningMachineService?.modifyMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString(),
                requireOwner = false,
                bypassPermission = player.hasPermission("cryptocurrency.admin")
            ) { machine ->
                machine.setMiningMemo(memo)
            } ?: false

            plugin.runSync {
                if (updated) {
                    player.sendMessage("$prefix§a採掘メモを設定しました: §f$memo")
                } else {
                    player.sendMessage("$prefix§c採掘メモの設定に失敗しました")
                }
            }
        }
    }

    private fun txMode(player: Player, machineId: String) {
        plugin.launchAsync {
            var newMode = ""
            val updated = plugin.miningMachineService?.modifyMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString(),
                requireOwner = false,
                bypassPermission = player.hasPermission("cryptocurrency.admin")
            ) { machine ->
                newMode = machine.toggleCreateBlockMode().name
                true
            } ?: false

            plugin.runSync {
                if (updated) {
                    player.sendMessage("$prefix§aTX収集モードを変更しました: §f$newMode")
                } else {
                    player.sendMessage("$prefix§cTX収集モードの変更に失敗しました")
                }
            }
        }
    }

    //block = nullなら次回勝手に作成なのでnull入れるだけでよい
    private fun recreateBlock(player: Player, machineId: String) {
        plugin.launchAsync {
            val updated = plugin.miningMachineService?.modifyMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString(),
                requireOwner = false,
                bypassPermission = player.hasPermission("cryptocurrency.admin")
            ) { machine ->
                machine.replaceMiningBlock(null)
                true
            } ?: false

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
            2 -> listOf(
                "setRewardPubKey",
                "setMemo",
                "txMode",
                "recreate"
            ).filter { it.startsWith(args[1]) }

            3 -> listOf("<machineId>").filter { it.startsWith(args[2]) }

            4 -> when (args[1]) {
                "setRewardPubKey" -> listOf("[index]").filter { it.startsWith(args[3]) }
                "setMemo" -> listOf("[memo]").filter { it.startsWith(args[3]) }
                else -> emptyList()
            }

            else -> emptyList()
        }
    }
}