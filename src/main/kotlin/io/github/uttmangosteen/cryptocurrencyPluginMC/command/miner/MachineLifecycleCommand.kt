package io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.entity.Player

class MachineLifecycleCommand(
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
            val removed = plugin.repositories.miningMachineRepo.delete(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString()
            )

            plugin.runSync {
                if (removed) {
                    player.sendMessage("$prefix§a採掘機を撤去しました")
                } else {
                    player.sendMessage("$prefix§c採掘機の撤去に失敗しました")
                }
            }
        }
    }

    private fun toggle(player: Player, machineId: String) {
        plugin.launchAsync {
            var enabled = false

            val updated = plugin.repositories.miningMachineRepo.updateMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString()
            ) { machine ->
                val toggled = machine.toggleEnabled()
                enabled = machine.enabled
                toggled
            }

            plugin.runSync {
                if (updated) {
                    if (enabled) {
                        player.sendMessage("$prefix§a採掘機を起動しました")
                    } else {
                        player.sendMessage("$prefix§c採掘機を停止しました")
                    }
                } else {
                    player.sendMessage("$prefix§c採掘機の電源切り替えに失敗しました")
                    player.sendMessage("$prefix§7起動する場合は、GPU、燃料、報酬口座を確認してください")
                }
            }
        }
    }

    private fun shareName(player: Player, machineId: String) {
        plugin.launchAsync {
            var enabled = false

            val updated = plugin.repositories.miningMachineRepo.updateMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString()
            ) { machine ->
                enabled = machine.toggleShareNameOnMined()
                true
            }

            plugin.runSync {
                if (updated) {
                    val status = if (enabled) "§aON" else "§cOFF"
                    player.sendMessage("$prefix§a採掘成功時の名前共有: $status")
                } else {
                    player.sendMessage("$prefix§c名前共有設定の切り替えに失敗しました")
                }
            }
        }
    }
}