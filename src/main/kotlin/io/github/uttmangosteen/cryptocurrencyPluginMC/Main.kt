package io.github.uttmangosteen.cryptocurrencyPluginMC

import io.github.uttmangosteen.cryptocurrencyPluginMC.command.AdminCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.MinerCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.UserCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.GpuConfig
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {

    override fun onEnable() {
        saveDefaultConfig()
        val config = Config.load(config)
        val gpuConfig = GpuConfig.load(this)

        getCommand("cc")?.setExecutor(UserCommand(this@Main))
        getCommand("ccmcn")?.setExecutor(MinerCommand(this@Main))

        val adminCommand = AdminCommand(this@Main, gpuConfig)
        getCommand("ccop")?.setExecutor(adminCommand)
        getCommand("ccop")?.tabCompleter = adminCommand
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
