package io.github.uttmangosteen.cryptocurrencyPluginMC.command.user

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.TextFormat.formatCoin
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class HistoryCommand(
    private val plugin: Main,
) {
    private val prefix = plugin.pluginConfig.prefix

    fun execute(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return

        plugin.launchAsync {
            val wallet = plugin.repositories.walletRepo.getWallet(sender.uniqueId.toString())

            plugin.runSync {
                if (wallet == null || wallet.accounts.isEmpty()) {
                    sender.sendMessage("$prefix§c口座が見つかりません")
                    return@runSync
                }

                val index = args.getOrNull(1)?.toIntOrNull() ?: 0
                val page = args.getOrNull(2)?.toIntOrNull() ?: 1

                val account = wallet.accounts.getOrNull(index)
                if (account == null) {
                    sender.sendMessage("$prefix§c口座が存在しません。")
                    return@runSync
                }

                val targetPubKey = account.publicKey

                plugin.launchAsync {
                    val histories = plugin.repositories.historyRepo.getHistory(targetPubKey)

                    plugin.runSync {
                        if (histories.isEmpty()) {
                            sender.sendMessage("$prefix§c取引履歴がありません。")
                            return@runSync
                        }

                        val groupedHistories = histories.groupBy { it.getString("txHash") }.values.toList()

                        val pagedGroups = groupedHistories
                            .chunked(8)
                            .getOrElse(page.coerceAtLeast(1) - 1) { emptyList() }
                            .reversed()

                        sender.sendMessage("$prefix§f§l========== §8§lTransaction history [$page] §f§l==========")

                        pagedGroups.forEach { outputs ->
                            val firstOutput = outputs.first()
                            val height = firstOutput.getInteger("height")
                            val memo = firstOutput.getString("memo") ?: "no memo"

                            val displayLines = mutableListOf<String>()

                            outputs.forEach { output ->
                                val amount = output.getLong("amount")
                                val receiver = output.getString("receiverPubKey")
                                val senderPubKey = output.getString("senderPubKey")

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
                                sender.sendMessage("$prefix§7height: §f$height §8| §7memo: §f$memo")
                                displayLines.forEach { line ->
                                    sender.sendMessage("$prefix$line")
                                }
                            }
                        }

                        sender.sendMessage("$prefix§f§l============================================")
                    }
                }
            }
        }
    }

    fun getTabCompletions(args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> listOf("[index]").filter { it.startsWith(args[1]) }
            3 -> listOf("[page]").filter { it.startsWith(args[2]) }
            else -> emptyList()
        }
    }
}