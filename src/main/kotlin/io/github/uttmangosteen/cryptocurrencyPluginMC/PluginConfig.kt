package io.github.uttmangosteen.cryptocurrencyPluginMC

import org.bukkit.configuration.file.FileConfiguration

data class PluginConfig(
    val mongodbConnectionString: String,
    val mongodbDatabase: String,

    val prefix: String,
    var enable: Boolean,
    val verboseLogging: Boolean,

    val mempoolLimitPerBlock: Int,

    val miningMachineMiningDelayTicks: Int,
    val miningMachineSaveIntervalMillis: Int,
) {
    companion object {
        private const val DEFAULT_MONGODB_CONNECTION_STRING = "mongodb://127.0.0.1:27017/?replicaSet=rs0"
        private const val DEFAULT_MONGODB_DATABASE = "cryptocurrency"

        private const val DEFAULT_PREFIX = "§f[§aCryptocurrency§f] "
        private const val DEFAULT_ENABLE = true
        private const val DEFAULT_VERBOSE_LOGGING = false

        private const val DEFAULT_MEMPOOL_LIMIT_PER_BLOCK = 100

        private const val DEFAULT_MINING_MACHINE_DELAY_TICKS = 20
        private const val DEFAULT_MINING_MACHINE_SAVE_INTERVAL_MILLIS = 10000

        fun load(config: FileConfiguration): PluginConfig {
            return PluginConfig(
                mongodbConnectionString = config.safeString("mongodb.connection-string", DEFAULT_MONGODB_CONNECTION_STRING),
                mongodbDatabase = config.safeString("mongodb.database", DEFAULT_MONGODB_DATABASE),

                prefix = config.safeString("plugin.prefix", DEFAULT_PREFIX),
                enable = config.getBoolean("plugin.enable", DEFAULT_ENABLE),
                verboseLogging = config.getBoolean("plugin.verbose-logging", DEFAULT_VERBOSE_LOGGING),

                mempoolLimitPerBlock = config.safeInt("blockchain.mempool-limit-per-block", DEFAULT_MEMPOOL_LIMIT_PER_BLOCK),

                miningMachineMiningDelayTicks = config.safeInt("mining-machine.delay-ticks", DEFAULT_MINING_MACHINE_DELAY_TICKS),
                miningMachineSaveIntervalMillis = config.safeInt("mining-machine.save-interval-millis", DEFAULT_MINING_MACHINE_SAVE_INTERVAL_MILLIS)
            )
        }

        //安全読み取り用
        private fun FileConfiguration.safeString(path: String, default: String): String {
            return getString(path, default)?.takeIf { it.isNotBlank() } ?: default
        }

        private fun FileConfiguration.safeInt(path: String, default: Int): Int {
            return getInt(path, default).coerceIn(0, Integer.MAX_VALUE)
        }

        private fun FileConfiguration.safeLong(path: String, default: Long): Long {
            return getLong(path, default).coerceIn(0L, Long.MAX_VALUE)
        }
    }
}