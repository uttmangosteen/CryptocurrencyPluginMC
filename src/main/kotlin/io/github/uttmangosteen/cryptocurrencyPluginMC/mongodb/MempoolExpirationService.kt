package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb

import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.repository.MempoolRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.repository.UtxoRepository
import java.util.logging.Logger

class MempoolExpirationService(
    provider: MongoDatabaseProvider,
    private val mempoolRepo: MempoolRepository,
    private val utxoRepo: UtxoRepository,
    private val logger: Logger
) {
    private val transactionRunner = MongoTransactionRunner(provider, logger)

    suspend fun expireOnStartup(ttlMillis: Long): Int {
        if (ttlMillis <= 0L) return 0

        val expiresBefore = System.currentTimeMillis() - ttlMillis
        val expiredCount = transactionRunner.run(
            operation = "failed to expire mempool transactions on startup",
            block = { session ->
                val expiredEntries = mempoolRepo.getExpiredBefore(session, expiresBefore)
                if (expiredEntries.isEmpty()) {
                    return@run MongoTransactionOutcome.Commit(0)
                }

                val transactionHashes = expiredEntries.map { it.txHash }
                if (!utxoRepo.unlockByTransactionIds(session, transactionHashes)) {
                    return@run MongoTransactionOutcome.Abort(0)
                }
                if (!mempoolRepo.deleteExpired(session, transactionHashes, expiresBefore)) {
                    return@run MongoTransactionOutcome.Abort(0)
                }

                MongoTransactionOutcome.Commit(expiredEntries.size)
            }
        ) ?: return 0

        if (expiredCount > 0) {
            mempoolRepo.invalidateMiningSelections()
            logger.ccInfo(
                LogComponent.MEMPOOL_REPOSITORY,
                "expired mempool transactions removed on startup",
                "transactionCount" to expiredCount,
                "expiresBefore" to expiresBefore
            )
        }
        return expiredCount
    }
}
