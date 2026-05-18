package io.github.uttmangosteen.cryptocurrencyPluginMC

import io.github.uttmangosteen.cryptocurrencyPluginMC.command.AdminCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.MinerCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.UserCommand
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.MongoDatabaseProvider
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.MongoRepositories
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.MiningMachineService
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.GpuConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {

    lateinit var mongoDatabaseProvider: MongoDatabaseProvider
        private set

    lateinit var repositories: MongoRepositories
        private set

    lateinit var pluginConfig: PluginConfig
        private set

    private val pluginScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var miningMachineService: MiningMachineService? = null

    override fun onEnable() {
        saveDefaultConfig()
        pluginConfig = PluginConfig.load(config)
        val gpuConfig = GpuConfig.load(this)

        logger.setVerbose(pluginConfig.verboseLogging)

        mongoDatabaseProvider = MongoDatabaseProvider(
            pluginConfig.mongodbConnectionString,
            pluginConfig.mongodbDatabase
        )
        repositories = MongoRepositories(
            provider = mongoDatabaseProvider,
            logger = logger,
            miningDelayTicks = pluginConfig.miningMachineMiningDelayTicks
        )

        pluginScope.launch {
            try {
                repositories.setupAll()
                server.scheduler.runTask(this@Main, Runnable {
                    registerCommands(gpuConfig)
                    miningMachineService = MiningMachineService(this@Main).also { it.start() }
                    logger.ccInfo(LogComponent.DATABASE, "setup completed")
                })

            } catch (e: Exception) {
                server.scheduler.runTask(this@Main, Runnable {
                    logger.ccSevere(LogComponent.DATABASE, "initialization failed", e)
                    server.pluginManager.disablePlugin(this@Main)
                })
            }
        }
    }

    private fun registerCommands(gpuConfig: GpuConfig) {
        val userCommand = UserCommand(this@Main)
        getCommand("cryptocurrency")?.tabCompleter = userCommand
        getCommand("cryptocurrency")?.setExecutor(userCommand)

        val minerCommand = MinerCommand(this@Main)
        getCommand("cryptocurrencymachine")?.setExecutor(minerCommand)
        getCommand("cryptocurrencymachine")?.tabCompleter = minerCommand

        val adminCommand = AdminCommand(this@Main, gpuConfig)
        getCommand("cryptocurrencyadmin")?.setExecutor(adminCommand)
        getCommand("cryptocurrencyadmin")?.tabCompleter = adminCommand
    }

    fun launchAsync(block: suspend CoroutineScope.() -> Unit): Job {
        return pluginScope.launch(block = block)
    }

    fun runSync(block: () -> Unit) {
        server.scheduler.runTask(this, Runnable {
            block()
        })
    }

    override fun onDisable() {
        miningMachineService?.stop()
        pluginScope.cancel()

        if (::mongoDatabaseProvider.isInitialized) {
            mongoDatabaseProvider.close()
        }
    }
}
