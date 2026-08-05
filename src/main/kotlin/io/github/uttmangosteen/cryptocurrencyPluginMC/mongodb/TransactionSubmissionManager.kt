package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.model.MempoolEntry
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.repository.MempoolRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.repository.UtxoRepository
import java.util.logging.Logger

enum class TransactionSubmissionResult {
    SUCCESS,
    UTXO_LOCK_FAILED,
    MEMPOOL_SAVE_FAILED,
    DATABASE_ERROR
}

class TransactionSubmissionManager(
    private val provider: MongoDatabaseProvider,
    private val utxoRepo: UtxoRepository,
    private val mempoolRepo: MempoolRepository,
    private val logger: Logger
) {
    private val transactionRunner = MongoTransactionRunner(provider, logger)

    suspend fun submit(transaction: Transaction, fee: Long): TransactionSubmissionResult {
        return transactionRunner.run(
            operation = "failed to submit transaction",
            block = { session ->
                if (!utxoRepo.lock(session, transaction)) {
                    return@run MongoTransactionOutcome.Abort(TransactionSubmissionResult.UTXO_LOCK_FAILED)
                }

                val entry = MempoolEntry(transaction = transaction, fee = fee)
                if (!mempoolRepo.save(session, entry)) {
                    return@run MongoTransactionOutcome.Abort(TransactionSubmissionResult.MEMPOOL_SAVE_FAILED)
                }

                MongoTransactionOutcome.Commit(TransactionSubmissionResult.SUCCESS)
            }
        ) ?: TransactionSubmissionResult.DATABASE_ERROR
    }
}
