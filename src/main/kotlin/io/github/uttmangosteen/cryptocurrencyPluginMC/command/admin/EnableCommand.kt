package io.github.uttmangosteen.cryptocurrencyPluginMC.command.admin

import io.github.uttmangosteen.cryptocurrencyPluginMC.Config
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class EnableCommand(
    private val plugin: JavaPlugin,
    private val config: Config
) {
    fun execute(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) return

        when (args[0]) {
            "run" -> {
                plugin.config.set("debug.enable", true)
                plugin.saveConfig()
                config.enable = true
                sender.sendMessage("§a仮想通貨を再開しました")
            }

            "halt" -> {
                plugin.config.set("debug.enable", false)
                plugin.saveConfig()
                config.enable = false
                sender.sendMessage("§c仮想通貨を停止しました")
            }
            else -> return
        }
    }
}