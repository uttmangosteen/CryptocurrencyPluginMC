package io.github.uttmangosteen.cryptocurrencyPluginMC

import org.bukkit.configuration.file.FileConfiguration

data class PluginConfig(
    val mongodbConnectionString: String,
    val mongodbDatabase: String,

    val prefix: String,
    var enable: Boolean,
    val verboseLogging: Boolean,

    val commandRateLimitMillis: Long,

    val mempoolLimitPerBlock: Int,

    val miningMachineMiningDelayTicks: Int,
    val miningMachineSaveIntervalMiningTicks: Int,
) {
    companion object {
        private const val DEFAULT_MONGODB_CONNECTION_STRING = "mongodb://127.0.0.1:27017/?replicaSet=rs0"
        private const val DEFAULT_MONGODB_DATABASE = "cryptocurrency"

        private const val DEFAULT_PREFIX = "§f[§aCryptocurrency§f] "
        private const val DEFAULT_ENABLE = true
        private const val DEFAULT_VERBOSE_LOGGING = false
        private const val DEFAULT_COMMAND_RATE_LIMIT_MILLIS = 500L

        private const val DEFAULT_MEMPOOL_LIMIT_PER_BLOCK = 100

        private const val DEFAULT_MINING_MACHINE_DELAY_TICKS = 20
        private const val DEFAULT_MINING_MACHINE_SAVE_INTERVAL_MINING_TICKS = 60

        fun load(config: FileConfiguration): PluginConfig {
            return PluginConfig(
                mongodbConnectionString = config.safeString(
                    "mongodb.connection-string",
                    DEFAULT_MONGODB_CONNECTION_STRING
                ),
                mongodbDatabase = config.safeString("mongodb.database", DEFAULT_MONGODB_DATABASE),

                prefix = config.safeString("plugin.prefix", DEFAULT_PREFIX),
                enable = config.getBoolean("plugin.enable", DEFAULT_ENABLE),
                verboseLogging = config.getBoolean("plugin.verbose-logging", DEFAULT_VERBOSE_LOGGING),
                commandRateLimitMillis = config.safeLong(
                    "plugin.command-rate-limit-ms",
                    DEFAULT_COMMAND_RATE_LIMIT_MILLIS
                ),

                mempoolLimitPerBlock = config.safeInt(
                    "blockchain.mempool-limit-per-block",
                    DEFAULT_MEMPOOL_LIMIT_PER_BLOCK
                ),

                miningMachineMiningDelayTicks = config.safeInt(
                    "mining-machine.delay-ticks",
                    DEFAULT_MINING_MACHINE_DELAY_TICKS
                ),
                miningMachineSaveIntervalMiningTicks = config.safeInt(
                    "mining-machine.save-interval-mining-ticks",
                    DEFAULT_MINING_MACHINE_SAVE_INTERVAL_MINING_TICKS
                )
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