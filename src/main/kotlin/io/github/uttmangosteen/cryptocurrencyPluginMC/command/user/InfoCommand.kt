package io.github.uttmangosteen.cryptocurrencyPluginMC.command.user

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.OutPoint
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class InfoCommand(private val plugin: Main) {
    private val prefix = plugin.pluginConfig.prefix
    private val displayService = TxMessageFactory()

    fun execute(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return
        val action = args.getOrNull(1) ?: return

        when (action) {
            "blockchain" -> showBlockchainInfo(sender)
            "mempool" -> showMempoolInfo(sender, args)
            else -> return
        }
    }

    private fun showBlockchainInfo(player: Player) {
        plugin.launchAsync {
            val latestBlock = plugin.repositories.blockRepo.getLatestBlock()
            val mempoolSize = plugin.repositories.mempoolRepo.getMempoolSize()

            plugin.runSync {
                player.sendMessage("$prefix§f§l========== §8§lBlockchain Status §f§l==========")
                if (latestBlock != null) {
                    player.sendMessage("$prefix§7height: §e${latestBlock.height}")
                    player.sendMessage("$prefix§7latest hash: §a${latestBlock.hash ?: "unknown"}")
                } else {
                    player.sendMessage("$prefix§7blockchain: §c not started")
                }
                player.sendMessage("$prefix§7mempool size: §6${mempoolSize} TXs")
                player.sendMessage("$prefix§f§l=======================================")
            }
        }
    }

    private fun showMempoolInfo(player: Player, args: Array<out String>) {
        val index = args.getOrNull(2)?.toIntOrNull() ?: 0

        plugin.launchAsync {
            val wallet = plugin.repositories.walletRepo.getWallet(player.uniqueId.toString())
            val account = wallet?.accounts?.getOrNull(index)

            if (account == null) {
                plugin.runSync {
                    player.sendMessage("$prefix§c口座が存在しません。")
                }
                return@launchAsync
            }

            val targetPubKey = account.publicKey
            val pendingEntries = plugin.repositories.mempoolRepo.getPendingTransactionsFor(targetPubKey)

            val outPoints = pendingEntries
                .flatMap { entry ->
                    entry.transaction.inputs.map { input ->
                        OutPoint(
                            txHash = input.prevTxHash,
                            outputIndex = input.outputIndex
                        )
                    }
                }
                .distinct()

            val utxoMap = plugin.repositories.utxoRepo.findUtxos(outPoints = outPoints)

            plugin.runSync {
                if (pendingEntries.isEmpty()) {
                    player.sendMessage("$prefix§c未承認の取引履歴がありません。")
                    return@runSync
                }

                player.sendMessage("$prefix§f§l========== §8§lPending Transaction §f§l==========")

                pendingEntries.forEach { entry ->
                    val tx = entry.transaction
                    val inputUtxos = tx.inputs.mapNotNull { input ->
                        utxoMap[
                            OutPoint(
                                txHash = input.prevTxHash,
                                outputIndex = input.outputIndex
                            )
                        ]
                    }

                    displayService.buildMessages(
                        prefix = prefix,
                        tx = tx,
                        targetPubKeys = setOf(targetPubKey),
                        inputUtxos = inputUtxos,
                        fee = entry.fee
                    ).forEach { message ->
                        player.sendMessage(message)
                    }
                }

                player.sendMessage("$prefix§f§l=========================================")
            }
        }
    }

    fun getTabCompletions(args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> listOf("blockchain", "mempool").filter { it.startsWith(args[1]) }
            3 -> when (args[1]) {
                "mempool" -> listOf("[index]").filter { it.startsWith(args[2]) }
                else -> emptyList()
            }

            else -> emptyList()
        }
    }
}