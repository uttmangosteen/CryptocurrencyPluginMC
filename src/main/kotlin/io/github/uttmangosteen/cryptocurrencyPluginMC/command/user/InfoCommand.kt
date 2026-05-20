package io.github.uttmangosteen.cryptocurrencyPluginMC.command.user

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.TextFormat.formatCoin
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class InfoCommand(private val plugin: Main) {
    private val prefix = plugin.pluginConfig.prefix

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
                    player.sendMessage("$prefix §7height: §e${latestBlock.height}")
                    player.sendMessage("$prefix §7latest hash: §a${latestBlock.hash ?: "unknown"}")
                } else {
                    player.sendMessage("$prefix §7blockchain: §c not started")
                }
                player.sendMessage("$prefix §7mempool size: §6${mempoolSize} TXs")
                player.sendMessage("$prefix§f§l=======================================")
            }
        }
    }

    private fun showMempoolInfo(player: Player, args: Array<out String>) {
        val index = args.getOrNull(2)?.toIntOrNull() ?: 0
        val page = args.getOrNull(3)?.toIntOrNull() ?: 1

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

            plugin.runSync {
                if (pendingEntries.isEmpty()) {
                    player.sendMessage("$prefix§c未承認の取引履歴がありません。")
                    return@runSync
                }

                val txPerPage = 8
                val pagedEntries = pendingEntries
                    .chunked(txPerPage)
                    .getOrElse(page.coerceAtLeast(1) - 1) { emptyList() }

                player.sendMessage("$prefix§f§l========== §8§lPending Transaction [$page] §f§l==========")

                pagedEntries.forEach { entry ->
                    val tx = entry.transaction
                    val memo = if (tx.memo.isNullOrEmpty()) "no memo" else tx.memo
                    val displayLines = mutableListOf<String>()
                    tx.outputs.forEach { output ->
                        val amount = output.amount
                        val receiver = output.receiverPubKey
                        val senderPubKey = tx.inputs.firstOrNull()?.publicKey
                        when {
                            senderPubKey == null -> {
                                if (receiver == targetPubKey) {
                                    displayLines.add("§a+${formatCoin(amount)} §7(coinbase)")
                                }
                            }

                            senderPubKey == targetPubKey -> {
                                if (receiver != targetPubKey) {
                                    displayLines.add("§c-${formatCoin(amount)} §7-> §8$receiver")
                                }
                            }

                            receiver == targetPubKey -> {
                                displayLines.add("§a+${formatCoin(amount)} §7<- §8$senderPubKey")
                            }
                        }
                    }
                    if (displayLines.isNotEmpty()) {
                        player.sendMessage("$prefix§7fee: §f${formatCoin(entry.fee)} §8| §7memo: §f$memo")
                        displayLines.forEach { line ->
                            player.sendMessage("$prefix$line")
                        }
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

            4 -> when (args[1]) {
                "mempool" -> listOf("[page]").filter { it.startsWith(args[3]) }
                else -> emptyList()
            }

            else -> emptyList()
        }
    }
}