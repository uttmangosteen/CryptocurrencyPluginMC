package io.github.uttmangosteen.cryptocurrencyPluginMC.command.admin

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.DatabaseMaintenanceService
import org.bukkit.command.CommandSender

class DatabaseCommand(
    private val plugin: Main
) {
    private val prefix = plugin.pluginConfig.prefix

    fun execute(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) return

        when (args[1]) {
            "reconnect" -> reconnect(sender)

            "flush" -> {
                if (!requireConfirm(sender, args)) return
                flush(sender)
            }

            "rebuild" -> {
                if (!requireConfirm(sender, args)) return
                rebuild(sender)
            }

            "verify" -> {
                if (!requireConfirm(sender, args)) return
                verify(sender)
            }

            "prune" -> {
                if (!requireConfirm(sender, args)) return
                prune(sender)
            }
        }
    }

    fun getTabCompletions(args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> listOf(
                "reconnect",
                "flush",
                "rebuild",
                "verify",
                "prune",
            ).filter { it.startsWith(args[1]) }

            // confirm は補完しない
            else -> emptyList()
        }
    }

    private fun reconnect(sender: CommandSender) {
        sender.sendMessage("$prefix§eMongoDBへ再接続しています...")

        plugin.launchAsync {
            val success = runCatching {
                plugin.reconnectDatabase()
            }.isSuccess

            plugin.runSync {
                if (success) {
                    sender.sendMessage("$prefix§aMongoDBへ再接続しました")
                } else {
                    sender.sendMessage("$prefix§cMongoDBへの再接続に失敗しました。コンソールを確認してください")
                }
            }
        }
    }

    private fun flush(sender: CommandSender) {
        sender.sendMessage("$prefix§eDBキャッシュを削除しています...")

        plugin.stopMiningMachineService()

        plugin.launchAsync {
            val success = plugin.databaseMaintenanceService.flushCaches()

            plugin.runSync {
                if (success) {
                    plugin.restartMiningMachineService()
                    sender.sendMessage("$prefix§aDBキャッシュを削除しました")
                    sender.sendMessage("$prefix§7削除対象: mempool, utxos, transaction_history")
                    sender.sendMessage("$prefix§7全採掘機は停止し、miningBlockをクリアしました")
                } else {
                    sender.sendMessage("$prefix§cDBキャッシュの削除に失敗しました。コンソールを確認してください")
                }
            }
        }
    }

    private fun rebuild(sender: CommandSender) {
        sender.sendMessage("$prefix§eblocks からDBキャッシュを再構築しています...")
        sender.sendMessage("$prefix§7この操作では blocks の厳密検証は行いません")

        plugin.stopMiningMachineService()

        plugin.launchAsync {
            val result = plugin.databaseMaintenanceService.rebuildCachesFromBlocks()

            plugin.runSync {
                when (result) {
                    is DatabaseMaintenanceService.RebuildResult.Success -> {
                        plugin.restartMiningMachineService()
                        sender.sendMessage("$prefix§aDBキャッシュを再構築しました")
                        sender.sendMessage("$prefix§7blocks: §f${result.blockCount}")
                        sender.sendMessage("$prefix§7utxos: §f${result.utxoCount}")
                        sender.sendMessage("$prefix§7transaction_history: §f${result.historyCount}")
                    }

                    is DatabaseMaintenanceService.RebuildResult.Failed -> {
                        sender.sendMessage("$prefix§cDBキャッシュの再構築に失敗しました")
                        sender.sendMessage("$prefix§c${result.message}")
                        sender.sendMessage("$prefix§e安全のため採掘サービスは停止したままです。原因を修正してから reconnect してください")
                    }
                }
            }
        }
    }

    private fun verify(sender: CommandSender) {
        sender.sendMessage("$prefix§eblocks を検証しています...")

        plugin.launchAsync {
            val result = plugin.databaseMaintenanceService.verifyBlocks()

            plugin.runSync {
                when (result) {
                    is DatabaseMaintenanceService.VerifyResult.Valid -> {
                        sender.sendMessage("$prefix§aチェーンは正常です")
                        sender.sendMessage("$prefix§7blocks: §f${result.blockCount}")
                    }

                    is DatabaseMaintenanceService.VerifyResult.Invalid -> {
                        sender.sendMessage("$prefix§cチェーンに不正なブロックがあります")
                        sender.sendMessage("$prefix§7invalid height: §f${result.invalidHeight}")
                        sender.sendMessage("$prefix§c${result.message}")
                        sender.sendMessage("$prefix§e修復する場合: §f/ccop database prune confirm")
                    }
                }
            }
        }
    }

    private fun prune(sender: CommandSender) {
        sender.sendMessage("$prefix§e不正ブロック以降を刈り取り、DBキャッシュを再構築しています...")

        plugin.stopMiningMachineService()

        plugin.launchAsync {
            val result = plugin.databaseMaintenanceService.pruneInvalidChainAndRebuild()

            plugin.runSync {
                when (result) {
                    is DatabaseMaintenanceService.PruneResult.NoInvalidBlock -> {
                        plugin.restartMiningMachineService()
                        sender.sendMessage("$prefix§a不正ブロックは見つかりませんでした")
                        sender.sendMessage("$prefix§7DBキャッシュは再構築済みです")
                    }

                    is DatabaseMaintenanceService.PruneResult.Pruned -> {
                        plugin.restartMiningMachineService()
                        sender.sendMessage("$prefix§a不正ブロック以降を削除しました")
                        sender.sendMessage("$prefix§7prunedFromHeight: §f${result.prunedFromHeight}")
                        sender.sendMessage("$prefix§7deletedBlocks: §f${result.deletedBlocks}")
                        sender.sendMessage("$prefix§7remainingBlocks: §f${result.remainingBlocks}")
                        sender.sendMessage("$prefix§7utxos: §f${result.utxoCount}")
                        sender.sendMessage("$prefix§7transaction_history: §f${result.historyCount}")
                    }

                    is DatabaseMaintenanceService.PruneResult.Failed -> {
                        sender.sendMessage("$prefix§cチェーン修復に失敗しました")
                        sender.sendMessage("$prefix§c${result.message}")
                        sender.sendMessage("$prefix§e安全のため採掘サービスは停止したままです。原因を修正してから reconnect してください")
                    }
                }
            }
        }
    }

    private fun requireConfirm(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.getOrNull(2) == "confirm") return true

        sender.sendMessage("$prefix§cこの操作は危険です。実行するには confirm を付けてください")
        sender.sendMessage("$prefix§7例: §f/ccop database ${args[1]} confirm")
        return false
    }
}