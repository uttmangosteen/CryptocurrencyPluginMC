package io.github.uttmangosteen.cryptocurrencyPluginMC.command

import io.github.uttmangosteen.cryptocurrencyPluginMC.command.admin.GetGpuCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.GpuConfig
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin

class AdminCommand(
    private val plugin: JavaPlugin,
    private val gpuConfig: GpuConfig
) : CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!sender.hasPermission("cryptocurrency.admin")) return true
        if (args.isEmpty()) return false

        when (args[0]) {
            "run", "halt" -> {
                //TODO:cc、ccmcnコマンドのonoff切り替え
            }
            "getGpu" -> {
                GetGpuCommand(plugin, gpuConfig).execute(sender, args)
            }
            "machine" -> {
                when (args[1]) {
                    "run" ,"halt"-> {
                        //TODO:全マシン稼働管理
                    }
                }
            }
            "database" -> {
                when (args[1]) {
                    "reconnection" -> {
                        //TODO:DB再接続
                    }
                    "verify" -> {
                        //TODO:Blocksから他コレクションを計算する
                    }
                }
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
    ): List<String> {
        if (command.name != "cryptocurrencyadmin") return emptyList()
        if (!sender.hasPermission("cryptocurrency.admin")) return emptyList()

        // args.size == 1 は「第1引数 (/ccop [ここ])」の入力中
        return when (args.size) {
            1 -> listOf(
                "run",
                "halt",
                "machine",
                "getGpu",
                "database"
            ).filter { it.startsWith(args[0]) }

            2 -> when (args[0]) {
                "machine" -> listOf(
                    "run",
                    "halt"
                ).filter { it.startsWith(args[1]) }

                "getGpu" -> gpuConfig.getTypes().filter { it.startsWith(args[1]) }

                "database" -> listOf(
                    "reconnection",
                    "verify"
                ).filter { it.startsWith(args[1]) }

                else -> emptyList()
            }

            else -> emptyList()
        }
    }
}