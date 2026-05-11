package io.github.uttmangosteen.cryptocurrencyPluginMC

import io.github.uttmangosteen.cryptocurrencyPluginMC.command.AdminCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.MinerCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.UserCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.MongoDatabaseProvider
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.MongoRepositories
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.GpuConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {

    lateinit var mongoDatabaseProvider: MongoDatabaseProvider
        private set

    lateinit var pluginConfig: PluginConfig
        private set

    private val pluginScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onEnable() {
        saveDefaultConfig()
        pluginConfig = PluginConfig.load(config)
        val gpuConfig = GpuConfig.load(this)

        logger.setVerbose(pluginConfig.verboseLogging)

        mongoDatabaseProvider = MongoDatabaseProvider(
            pluginConfig.mongodbConnectionString,
            pluginConfig.mongodbDatabase
        )
        val repositories = MongoRepositories(mongoDatabaseProvider, logger)

        pluginScope.launch {
            try {
                repositories.setupAll()


            } catch (e: Exception) {
                server.scheduler.runTask(this@Main, Runnable {
                    logger.ccSevere(LogComponent.DATABASE, "initialization failed", e)
                    server.pluginManager.disablePlugin(this@Main)
                })
            }
        }

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
