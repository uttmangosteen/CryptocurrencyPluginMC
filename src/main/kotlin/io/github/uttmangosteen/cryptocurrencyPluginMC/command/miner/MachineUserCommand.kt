package io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class MachineUserCommand(
    private val plugin: Main
) {
    private val prefix = plugin.pluginConfig.prefix

    fun execute(player: Player, args: Array<out String>) {
        val action = args.getOrNull(1) ?: return
        val machineId = args.getOrNull(2) ?: return
        val name = args.getOrNull(3) ?: return

        when (action.lowercase()) {
            "add" -> addUser(player, machineId, name)
            "delete" -> deleteUser(player, machineId, name)
        }
    }

    private fun addUser(player: Player, machineId: String, name: String) {
        val targetUuid = Bukkit.getOfflinePlayer(name).uniqueId.toString()

        plugin.launchAsync {
            val updated = plugin.miningMachineService?.modifyMachineExternal(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString(),
                requireOwner = true
            ) { machine ->
                machine.addUser(targetUuid)
            } ?: false

            plugin.runSync {
                if (updated) {
                    player.sendMessage("$prefix§a$name を採掘機ユーザーに追加しました")
                } else {
                    player.sendMessage("$prefix§cユーザー追加に失敗しました")
                }
            }
        }
    }

    private fun deleteUser(player: Player, machineId: String, name: String) {
        val targetUuid = Bukkit.getOfflinePlayer(name).uniqueId.toString()

        plugin.launchAsync {
            val updated = plugin.miningMachineService?.modifyMachineExternal(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString(),
                requireOwner = true
            ) { machine ->
                machine.removeUser(targetUuid)
            } ?: false

            plugin.runSync {
                if (updated) {
                    player.sendMessage("$prefix§a$name を採掘機ユーザーから削除しました")
                } else {
                    player.sendMessage("$prefix§cユーザー削除に失敗しました")
                }
            }
        }
    }

    fun getTabCompletions(args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> listOf("add", "delete").filter { it.startsWith(args[1]) }
            3 -> listOf("<machineId>").filter { it.startsWith(args[2]) }
            4 -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[3]) }
            else -> emptyList()
        }
    }
}