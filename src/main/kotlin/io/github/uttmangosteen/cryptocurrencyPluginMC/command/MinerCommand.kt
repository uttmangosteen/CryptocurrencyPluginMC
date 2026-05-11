package io.github.uttmangosteen.cryptocurrencyPluginMC.command

import io.github.uttmangosteen.cryptocurrencyPluginMC.Config
import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class MinerCommand(
    private val plugin: Main,
    private val config: Config
) : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!config.enable) return true
        if (!sender.hasPermission("cryptocurrency.miner")) return true
        if (args.isEmpty()) return false

        when (args[0]) {
            else -> return true
        }
    }
}