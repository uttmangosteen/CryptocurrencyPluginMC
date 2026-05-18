package io.github.uttmangosteen.cryptocurrencyPluginMC.command.user

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.TextFormat
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class BalanceCommand(
    private val plugin: Main
) {
    private val prefix = plugin.pluginConfig.prefix

    fun execute(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return

        val index = args.getOrNull(1)?.toIntOrNull() ?: 0

        plugin.launchAsync {
            val account = plugin.repositories.walletRepo.getWallet(sender.uniqueId.toString())
                ?.accounts
                ?.getOrNull(index)

            if (account == null) {
                plugin.runSync {
                    sender.sendMessage("$prefix§c指定された口座がありません")
                }
                return@launchAsync
            }

            val walletState = plugin.repositories.utxoRepo.getWalletState(account.publicKey)

            val formattedTotalBalance = TextFormat.formatCoin(walletState.totalBalance)
            val formattedAvailableBalance = TextFormat.formatCoin(walletState.balance)
            val formattedPendingBalance = TextFormat.formatCoin(walletState.pendingBalance)

            plugin.runSync {
                val mainMark = if (index == 0) "§a§lMAIN " else ""
                val keyMark = if (account.privateKey == null) "§e§lWATCH " else ""
                val memo = account.memo.ifBlank { "§7no memo" }

                sender.sendMessage("$prefix§f§l========== §8§lBalance §f§l==========")
                sender.sendMessage("$prefix§f[$index] $mainMark$keyMark§r$memo")
                sender.sendMessage("$prefix§8${account.publicKey}")

                sender.sendMessage("$prefix§7total: §a$formattedTotalBalance")
                sender.sendMessage("$prefix§7available: §a$formattedAvailableBalance")
                sender.sendMessage("$prefix§7pending: §e$formattedPendingBalance")
                sender.sendMessage("$prefix§f§l=============================")
            }
        }
    }

    fun getTabCompletions(args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> listOf("[index]").filter { it.startsWith(args[1]) }
            else -> emptyList()
        }
    }
}