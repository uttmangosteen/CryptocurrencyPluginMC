package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb

import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.mempool.MempoolRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.miningmachine.MiningMachineRepository
import java.util.logging.Logger

class MongoRepositories(
    provider: MongoDatabaseProvider,
    logger: Logger
) {
    val miningMachineRepo = MiningMachineRepository(provider.database, logger)
    val mempoolRepo = MempoolRepository(provider.database, logger)

    suspend fun setupAll() {
        miningMachineRepo.setup()
        mempoolRepo.setup()
    }
}