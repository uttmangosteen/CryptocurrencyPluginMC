package io.github.uttmangosteen.cryptocurrencyPluginMC.command

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.user.AccountCommand
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import kotlin.text.startsWith

class UserCommand(
    private val plugin: Main,
) : CommandExecutor, TabCompleter {
    private val accountCommand = AccountCommand(plugin)
    // 今後、WalletCommand や SendCommand を作ったらここに並べていく
    // private val walletCommand = WalletCommand(plugin)
    // private val sendCommand = SendCommand(plugin)

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!plugin.pluginConfig.enable) return true
        if (!sender.hasPermission("cryptocurrency.user")) return true

        // 💡 引数が空の場合は false を返すことで、plugin.yml に書いた美しい help (usage) が自動表示されます
        if (args.isEmpty()) return false

        when (args[0]) {
            "account" -> accountCommand.execute(sender, args)
            // "wallet" -> walletCommand.execute(sender, args)
            // "balance" -> balanceCommand.execute(sender, args)
            // "history" -> historyCommand.execute(sender, args)
            // "send" -> sendCommand.execute(sender, args)
            // "info" -> infoCommand.execute(sender, args)
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
        if (command.name != "cryptocurrency") return emptyList()
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

                // 💡 タブ補完も同様に、ここに対応するクラスの補完ロジックを流すだけ
                // "wallet" -> walletCommand.getTabCompletions(args)
                // "send" -> sendCommand.getTabCompletions(args)

                else -> emptyList()
            }
        }
    }
}