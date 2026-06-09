package io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

class MachineUserCommand(
    private val plugin: Main
) {
    private val prefix = plugin.pluginConfig.prefix

    fun execute(player: Player, args: Array<out String>) {
        val action = args.getOrNull(1) ?: return
        val machineId = args.getOrNull(2) ?: return

        when (action.lowercase()) {
            "add" -> {
                val name = args.getOrNull(3) ?: return
                addUser(player, machineId, name)
            }

            "delete" -> {
                val name = args.getOrNull(3) ?: return
                deleteUser(player, machineId, name)
            }

            "list" -> listUsers(player, machineId)
        }
    }

    private fun addUser(player: Player, machineId: String, name: String) {
        val targetUuid = resolveKnownPlayerUuid(name) ?: run {
            player.sendMessage("$prefix§c指定されたプレイヤーが見つかりません")
            player.sendMessage("$prefix§7オンライン中の名前、過去に参加済みの名前、またはUUIDを指定してください")
            return
        }

        plugin.launchAsync {
            val updated = plugin.miningMachineService?.modifyMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString(),
                requireOwner = true,
                bypassPermission = player.hasPermission("cryptocurrency.admin")
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
        val targetUuid = resolveKnownPlayerUuid(name) ?: run {
            player.sendMessage("$prefix§c指定されたプレイヤーが見つかりません")
            player.sendMessage("$prefix§7/ccmcn user list $machineId でUUIDを確認して指定してください")
            return
        }

        plugin.launchAsync {
            val updated = plugin.miningMachineService?.modifyMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString(),
                requireOwner = true,
                bypassPermission = player.hasPermission("cryptocurrency.admin")
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

    private fun listUsers(player: Player, machineId: String) {
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

                val ownerName = machine.userUuids.firstOrNull()?.let { resolveUuidDisplayName(it) }

                val usersText =
                    machine.userUuids.drop(1).joinToString(", ") { resolveUuidDisplayName(it) }.ifBlank { "§7none" }

                player.sendMessage("$prefix§f§l========== §8§lMining Machine Users §f§l==========")
                player.sendMessage("$prefix§a§lOwner:§f $ownerName")
                player.sendMessage("$prefix§a§lUser:§e $usersText")
                player.sendMessage("$prefix§f§l==========================================")
            }
        }
    }

    private fun resolveKnownPlayerUuid(input: String): String? {
        val uuid = runCatching {
            UUID.fromString(input)
        }.getOrNull()

        if (uuid != null) return uuid.toString()

        Bukkit.getPlayerExact(input)?.let { player ->
            return player.uniqueId.toString()
        }

        return Bukkit.getOfflinePlayers()
            .firstOrNull { offlinePlayer ->
                offlinePlayer.name.equals(input, ignoreCase = true)
            }
            ?.uniqueId
            ?.toString()
    }

    private fun resolveUuidDisplayName(uuidString: String): String {
        val uuid = runCatching { UUID.fromString(uuidString) }.getOrNull() ?: return "null"
        return Bukkit.getOfflinePlayer(uuid).name ?: uuidString
    }

    fun getTabCompletions(args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> listOf("add", "delete", "list").filter { it.startsWith(args[1]) }
            3 -> listOf("<machineId>").filter { it.startsWith(args[2]) }
            4 -> when (args[1].lowercase()) {
                "add", "delete" -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[3]) }
                else -> emptyList()
            }

            else -> emptyList()
        }
    }
}