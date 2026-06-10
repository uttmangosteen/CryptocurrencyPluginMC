package io.github.uttmangosteen.cryptocurrencyPluginMC.command

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner.MachineBlockCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner.MachineFuelCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner.MachineGpuCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner.MachineInfoCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner.MachineOpenCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner.MachineSettingCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner.MachineUserCommand
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class MinerCommand(
    private val plugin: Main,
    private val commandRateLimiter: CommandRateLimiter,
) : CommandExecutor, TabCompleter {
    private val lifecycleCommand = MachineSettingCommand(plugin)
    private val infoCommand = MachineInfoCommand(plugin)
    private val openCommand = MachineOpenCommand(plugin)
    private val gpuCommand = MachineGpuCommand(plugin)
    private val userCommand = MachineUserCommand(plugin)
    private val fuelCommand = MachineFuelCommand(plugin)
    private val blockCommand = MachineBlockCommand(plugin)

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!plugin.pluginConfig.enable) return true
        if (!sender.hasPermission("cryptocurrency.miner")) return true
        if (sender !is Player) return true

        if (!commandRateLimiter.tryAcquire(sender)) {
            sender.sendMessage("${plugin.pluginConfig.prefix}§cコマンドの実行が速すぎます")
            return true
        }

        if (args.isEmpty()) return false

        when (args[0]) {
            "toggle", "shareName" -> lifecycleCommand.execute(sender, args)
            "info" -> infoCommand.execute(sender, args)
            "open" -> openCommand.execute(sender, args)
            "gpu" -> gpuCommand.execute(sender, args)
            "user" -> userCommand.execute(sender, args)
            "fuel" -> fuelCommand.execute(sender, args)
            "block" -> blockCommand.execute(sender, args)
            else -> return true
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (!sender.hasPermission("cryptocurrency.miner")) return emptyList()

        return when (args.size) {
            1 -> listOf(
                "info",
                "open",
                "gpu",
                "user",
                "toggle",
                "shareName",
                "fuel",
                "block"
            ).filter { it.startsWith(args[0]) }

            else -> when (args[0]) {
                "gpu" -> gpuCommand.getTabCompletions(args)
                "user" -> userCommand.getTabCompletions(args)
                "block" -> blockCommand.getTabCompletions(args)
                "info", "open", "toggle", "shareName", "fuel" -> {
                    if (args.size == 2) listOf("<machineId>").filter { it.startsWith(args[1]) } else emptyList()
                }

                else -> emptyList()
            }
        }
    }
}