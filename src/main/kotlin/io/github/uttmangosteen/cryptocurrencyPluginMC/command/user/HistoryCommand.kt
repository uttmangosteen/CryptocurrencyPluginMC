package io.github.uttmangosteen.cryptocurrencyPluginMC.command.user

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class HistoryCommand(
    private val plugin: Main,
) {
    private val prefix = plugin.pluginConfig.prefix
    private val displayService = TxMessageFactory()

    fun execute(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return

        plugin.launchAsync {
            val wallet = plugin.repositories.walletRepo.getWallet(sender.uniqueId.toString())

            if (wallet == null || wallet.accounts.isEmpty()) {
                plugin.runSync {
                    sender.sendMessage("$prefix§c口座が見つかりません")
                }
                return@launchAsync
            }

            val index = args.getOrNull(1)?.toIntOrNull() ?: 0
            val page = args.getOrNull(2)?.toIntOrNull() ?: 1

            val account = wallet.accounts.getOrNull(index)
            if (account == null) {
                plugin.runSync {
                    sender.sendMessage("$prefix§c口座が存在しません。")
                }
                return@launchAsync
            }

            val targetPubKeys = setOf(account.publicKey)
            val histories = plugin.repositories.historyRepo.getHistory(account.publicKey)

            plugin.runSync {
                if (histories.isEmpty()) {
                    sender.sendMessage("$prefix§c取引履歴がありません。")
                    return@runSync
                }

                val pagedHistories = histories
                    .chunked(8)
                    .getOrElse(page.coerceAtLeast(1) - 1) { emptyList() }

                sender.sendMessage("$prefix§f§l========== §8§lTransaction history [$page] §f§l==========")

                pagedHistories.forEach { history ->
                    sender.sendMessage("$prefix§f§l========== Transaction History ==========")
                    sender.sendMessage("$prefix§7height: §f${history.height} §8| §7txHash: §8${history.txHash}")

                    displayService.buildMessages(
                        prefix = prefix,
                        tx = history.transaction,
                        targetPubKeys = targetPubKeys,
                        inputUtxos = history.inputUtxos,
                        fee = history.fee
                    ).forEach { message ->
                        sender.sendMessage(message)
                    }
                }

                sender.sendMessage("$prefix§f§l============================================")
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