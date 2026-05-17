package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb

import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.mempool.MempoolRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.utxo.UtxoRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.wallet.WalletRepository
import java.util.logging.Logger

class MongoRepositories(
    provider: MongoDatabaseProvider,
    logger: Logger
) {
    val mempoolRepo = MempoolRepository(provider.database, logger)
    val walletRepo = WalletRepository(provider.database, logger)
    val utxoRepo = UtxoRepository(provider.database, logger)

    suspend fun setupAll() {
        mempoolRepo.setup()
        walletRepo.setup()
        utxoRepo.setup()
    }
}