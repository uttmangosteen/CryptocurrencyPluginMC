package io.github.uttmangosteen.cryptocurrencyPluginMC.command

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.admin.DatabaseCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.admin.EnableCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.admin.GetGpuCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.admin.MachineCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.GpuConfig
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class AdminCommand(
    plugin: Main,
    private val gpuConfig: GpuConfig
) : CommandExecutor, TabCompleter {
    private val enableCommand = EnableCommand(plugin)
    private val getGpuCommand = GetGpuCommand(plugin, gpuConfig)
    private val databaseCommand = DatabaseCommand(plugin)
    private val machineCommand = MachineCommand(plugin)

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!sender.hasPermission("cryptocurrency.admin")) return true
        if (args.isEmpty()) return false

        when (args[0]) {
            "create", "remove" -> {
                machineCommand.execute(args)
            }

            "run", "halt" -> {
                enableCommand.execute(sender, args)
            }

            "getGpu" -> {
                getGpuCommand.execute(sender, args)
            }

            "database" -> {
                databaseCommand.execute(sender, args)
            }

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
        if (!sender.hasPermission("cryptocurrency.admin")) return emptyList()

        return when (args.size) {
            1 -> listOf(
                "create",
                "remove",
                "run",
                "halt",
                "getGpu",
                "database"
            ).filter { it.startsWith(args[0]) }

            else -> when (args[0]) {
                "create" -> {
                    when (args.size) {
                        2 -> listOf("<machineId>").filter { it.startsWith(args[1]) }
                        3 -> null
                        else -> emptyList()
                    }
                }

                "remove" -> {
                    if (args.size == 2) listOf("<machineId>").filter { it.startsWith(args[1]) } else emptyList()
                }

                "getGpu" -> {
                    if (args.size == 2) {
                        gpuConfig.getTypes().filter { it.startsWith(args[1]) }
                    } else {
                        emptyList()
                    }
                }

                "database" -> databaseCommand.getTabCompletions(args)

                else -> emptyList()
            }
        }
    }
}