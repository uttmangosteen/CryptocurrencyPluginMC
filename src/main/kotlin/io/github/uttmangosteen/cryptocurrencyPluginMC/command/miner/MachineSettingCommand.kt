package io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.entity.Player

class MachineSettingCommand(
    private val plugin: Main
) {
    private val prefix = plugin.pluginConfig.prefix

    fun execute(player: Player, args: Array<out String>) {
        when (args[0]) {
            "create" -> create(player)

            "remove" -> {
                val machineId = args.getOrNull(1) ?: return
                remove(player, machineId)
            }

            "toggle" -> {
                val machineId = args.getOrNull(1) ?: return
                toggle(player, machineId)
            }

            "shareName" -> {
                val machineId = args.getOrNull(1) ?: return
                shareName(player, machineId)
            }
        }
    }

    private fun create(player: Player) {
        plugin.launchAsync {
            val machine = plugin.repositories.miningMachineRepo.create(player.uniqueId.toString())
            plugin.runSync {
                if (machine != null) {
                    player.sendMessage("$prefix§a採掘機を作成しました")
                    player.sendMessage("$prefix§7machineId: §f${machine.id}")
                } else {
                    player.sendMessage("$prefix§c採掘機の作成に失敗しました")
                }
            }
        }
    }

    private fun remove(player: Player, machineId: String) {
        plugin.launchAsync {
            val uuid = player.uniqueId.toString()

            // メモリにあったらhaltして消す
            plugin.miningMachineService?.modifyMachine(
                machineId = machineId,
                requesterUuid = uuid,
                requireOwner = true,
                bypassPermission = player.hasPermission("cryptocurrency.admin")
            ) { machine ->
                machine.halt()
                true
            }

            // DBから完全に削除
            val deleted = plugin.repositories.miningMachineRepo.delete(
                machineId = machineId,
                requesterUuid = uuid,
                bypassPermission = player.hasPermission("cryptocurrency.admin")
            )

            plugin.runSync {
                if (deleted) {
                    player.sendMessage("$prefix§a採掘機(§f$machineId§a)を削除しました")
                } else {
                    player.sendMessage("$prefix§c削除に失敗しました(存在しないかオーナーではありません)")
                }
            }
        }
    }

    private fun toggle(player: Player, machineId: String) {
        plugin.launchAsync {
            var enabled = false

            val updated = plugin.miningMachineService?.modifyMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString(),
                requireOwner = false,
                bypassPermission = player.hasPermission("cryptocurrency.admin")
            ) { machine ->
                val toggled = machine.toggleEnabled()
                enabled = machine.enabled
                toggled
            } ?: false

            plugin.runSync {
                if (updated) {
                    if (enabled) {
                        player.sendMessage("$prefix§a採掘機を起動しました")
                    } else {
                        player.sendMessage("$prefix§c採掘機を停止しました")
                    }
                } else {
                    player.sendMessage("$prefix§c採掘機の電源切り替えに失敗しました。GPU、燃料、報酬口座を確認してください")
                }
            }
        }
    }

    private fun shareName(player: Player, machineId: String) {
        plugin.launchAsync {
            var enabled = false

            val updated = plugin.miningMachineService?.modifyMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString(),
                requireOwner = false,
                bypassPermission = player.hasPermission("cryptocurrency.admin")
            ) { machine ->
                enabled = machine.toggleShareNameOnMined()
                true
            } ?: false

            plugin.runSync {
                if (updated) {
                    val status = if (enabled) "§aON" else "§cOFF"
                    player.sendMessage("$prefix§f採掘成功時の名前共有: $status")
                } else {
                    player.sendMessage("$prefix§c名前共有設定の切り替えに失敗しました")
                }
            }
        }
    }
}