package io.github.uttmangosteen.cryptocurrencyPluginMC.command

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class MinerCommand(
    private val plugin: Main,
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!plugin.pluginConfig.enable) return true
        if (!sender.hasPermission("cryptocurrency.miner")) return true
        if (args.isEmpty()) return false // 💡 引数が空なら usage 表示

        when (args[0]) {
            "create" -> { /* TODO */ }
            "remove" -> { /* TODO */ }
            "info" -> { /* TODO */ }
            "open" -> { /* TODO */ }
            "set", "take" -> { /* TODO: GPUの脱着 */ }
            "user" -> { /* TODO: add/delete 共同作業者 */ }
            "run", "halt" -> { /* TODO: マシンの電源 */ }
            "fuel" -> { /* TODO: 燃料投入口UI */ }
            "block" -> {
                if (args.size < 2) return false
                when (args[1]) {
                    "setdefaultmemo" -> { /* TODO */ }
                    "setmemo" -> { /* TODO */ }
                    "txmode" -> { /* TODO */ }
                    "recreate" -> { /* TODO */ }
                    else -> return false
                }
            }
            else -> return false
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (command.name != "cryptocurrencymachine") return emptyList()
        if (!sender.hasPermission("cryptocurrency.miner")) return emptyList()

        return when (args.size) {
            1 -> listOf("create", "remove", "info", "open", "set", "take", "user", "run", "halt", "fuel", "block")
                .filter { it.startsWith(args[0]) }

            2 -> when (args[0].lowercase()) {
                "block" -> listOf("setDefaultMemo", "setMemo", "txMode", "recreate")
                    .filter { it.startsWith(args[1]) }
                "user" -> listOf("add", "delete").filter { it.startsWith(args[1]) }
                else -> listOf("<machineId>").filter { it.startsWith(args[1]) } // ID入力補助
            }

            else -> emptyList()
        }
    }
}