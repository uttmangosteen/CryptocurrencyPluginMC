package io.github.uttmangosteen.cryptocurrencyPluginMC.command.admin

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.command.CommandSender

class EnableCommand(
    private val plugin: Main,
) {
    private val prefix = plugin.pluginConfig.prefix

    fun execute(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) return

        when (args[0]) {
            "run" -> {
                plugin.config.set("plugin.enable", true)
                plugin.saveConfig()
                plugin.pluginConfig.enable = true
                sender.sendMessage("$prefix§a仮想通貨を再開しました")
            }

            "halt" -> {
                plugin.config.set("plugin.enable", false)
                plugin.saveConfig()
                plugin.pluginConfig.enable = false
                sender.sendMessage("$prefix§c仮想通貨を停止しました")
            }

            else -> return
        }
    }
}