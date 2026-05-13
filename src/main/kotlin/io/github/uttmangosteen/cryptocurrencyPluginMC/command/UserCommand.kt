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
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!plugin.pluginConfig.enable) return true
        if (!sender.hasPermission("cryptocurrency.user")) return true
        if (args.isEmpty()) return false

        when (args[0]) {
            "account" -> AccountCommand(plugin).execute(sender, args)

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
                "account" -> AccountCommand(plugin).getTabCompletions(args)


                else -> emptyList()
            }
        }
    }
}