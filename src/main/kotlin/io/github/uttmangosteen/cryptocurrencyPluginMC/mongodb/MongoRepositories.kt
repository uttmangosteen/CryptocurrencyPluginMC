package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb

import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.mempool.MempoolRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.wallet.WalletRepository
import java.util.logging.Logger

class MongoRepositories(
    provider: MongoDatabaseProvider,
    logger: Logger
) {
    val mempoolRepo = MempoolRepository(provider.database, logger)
    val walletRepo = WalletRepository(provider.database, logger)

    suspend fun setupAll() {
        mempoolRepo.setup()
        walletRepo.setup()
    }
}