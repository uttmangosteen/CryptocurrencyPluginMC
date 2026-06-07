package io.github.uttmangosteen.cryptocurrencyPluginMC.command

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.user.AccountCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.user.BalanceCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.user.HistoryCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.user.InfoCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.user.SendCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.user.WalletCommand
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import kotlin.text.startsWith

class UserCommand(
    private val plugin: Main,
    private val commandRateLimiter: CommandRateLimiter,
) : CommandExecutor, TabCompleter {
    private val walletCommand = WalletCommand(plugin)
    private val accountCommand = AccountCommand(plugin)
    private val balanceCommand = BalanceCommand(plugin)
    private val historyCommand = HistoryCommand(plugin)
    private val sendCommand = SendCommand(plugin)
    private val infoCommand = InfoCommand(plugin)

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!plugin.pluginConfig.enable) return true
        if (!sender.hasPermission("cryptocurrency.user")) return true

        if (!commandRateLimiter.tryAcquire(sender)) {
            sender.sendMessage("${plugin.pluginConfig.prefix}§cコマンドの実行が速すぎます")
            return true
        }

        if (args.isEmpty()) return false

        when (args[0]) {
            "wallet" -> walletCommand.execute(sender)
            "account" -> accountCommand.execute(sender, args)
            "balance" -> balanceCommand.execute(sender, args)
            "history" -> historyCommand.execute(sender, args)
            "send" -> sendCommand.execute(sender, args)
            "info" -> infoCommand.execute(sender, args)
            else -> return true
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!sender.hasPermission("cryptocurrency.user")) return emptyList()

        return when (args.size) {
            1 -> listOf(
                "wallet",
                "account",
                "balance",
                "history",
                "send",
                "info"
            ).filter { it.startsWith(args[0]) }

            else -> when (args[0].lowercase()) {
                "account" -> accountCommand.getTabCompletions(args)
                "balance" -> balanceCommand.getTabCompletions(args)
                "history" -> historyCommand.getTabCompletions(args)
                "send" -> sendCommand.getTabCompletions(args)
                "info" -> infoCommand.getTabCompletions(args)
                else -> emptyList()
            }
        }
    }
}