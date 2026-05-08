package io.github.uttmangosteen.cryptocurrencyPluginMC

import io.github.uttmangosteen.cryptocurrencyPluginMC.command.AdminCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.MinerCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.UserCommand
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {

    override fun onEnable() {


        getCommand("cryptocurrency")?.setExecutor(UserCommand(this@Main))
        getCommand("cryptocurrencymachine")?.setExecutor(MinerCommand(this@Main))
        getCommand("cryptocurrencyadmin")?.setExecutor(AdminCommand(this@Main))
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
