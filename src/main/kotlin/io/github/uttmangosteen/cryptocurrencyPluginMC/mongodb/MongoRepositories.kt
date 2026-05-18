package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb

import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.BlockchainManager
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.blocks.BlockRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.mempool.MempoolRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.transactionhistory.TransactionHistoryRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.utxo.UtxoRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.miningmachine.MiningMachineRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.wallet.WalletRepository
import java.util.logging.Logger

class MongoRepositories(
    provider: MongoDatabaseProvider,
    logger: Logger,
    miningDelayTicks: Int
) {
    val walletRepo = WalletRepository(provider.database, logger)

    val blockRepo = BlockRepository(provider.database, logger)
    val utxoRepo = UtxoRepository(provider.database, logger)
    val historyRepo = TransactionHistoryRepository(provider.database, logger)
    val mempoolRepo = MempoolRepository(provider.database, logger)

    val miningMachineRepo = MiningMachineRepository(provider.database, logger)

    val blockchainManager = BlockchainManager(
        provider = provider,
        blockRepo = blockRepo,
        utxoRepo = utxoRepo,
        historyRepo = historyRepo,
        mempoolRepo = mempoolRepo,
        miningDelayTicks = miningDelayTicks,
        logger = logger
    )

    suspend fun setupAll() {
        walletRepo.setup()
        utxoRepo.setup()
        blockRepo.setup()
        historyRepo.setup()
        mempoolRepo.setup()
        miningMachineRepo.setup()
    }
}