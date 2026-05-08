package io.github.uttmangosteen.cryptocurrencyPluginMC.command

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class AdminCommand(private val plugin: Main) : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!sender.hasPermission("cryptocurrency.admin")) return true
        if (args.isEmpty()) return false

        when (args[0].lowercase()) {
            else -> return true
        }
    }
}