package io.github.uttmangosteen.cryptocurrencyPluginMC

import io.github.uttmangosteen.cryptocurrencyPluginMC.command.AdminCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.MinerCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.UserCommand
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {

    override fun onEnable() {


        getCommand("cc")?.setExecutor(UserCommand(this@Main))
        getCommand("ccmcn")?.setExecutor(MinerCommand(this@Main))
        getCommand("ccop")?.setExecutor(AdminCommand(this@Main))
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
