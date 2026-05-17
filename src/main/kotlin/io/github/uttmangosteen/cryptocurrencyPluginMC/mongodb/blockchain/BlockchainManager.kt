package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain

import com.mongodb.MongoCommandException
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.MongoDatabaseProvider
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.blocks.BlockRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.mempool.MempoolRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.utxo.UtxoRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.transactionhistory.TransactionHistoryRepository
import java.util.logging.Logger

class BlockchainManager(
    private val provider: MongoDatabaseProvider,
    private val blockRepo: BlockRepository,
    private val utxoRepo: UtxoRepository,
    private val historyRepo: TransactionHistoryRepository,
    private val mempoolRepo: MempoolRepository,
    private val logger: Logger
) {
    //blockchain積み上げ、使ったutxo変換、確定分history記述、確定txをmempoolから削除
    suspend fun acceptNewBlock(block: Block): Boolean {

        val session = provider.startSession()

        return try {
            session.startTransaction()

            val blockSaved = blockRepo.saveBlock(session, block)
            if (!blockSaved) throw RuntimeException("failed to save block document")

            val utxoApplied = utxoRepo.applyTransactions(session, block.transactions)
            if (!utxoApplied) throw RuntimeException("failed to update UTXO set")

            val historyWritten = historyRepo.writeHistory(session, block.transactions, block.height, block.timestamp)
            if (!historyWritten) throw RuntimeException("failed to write transaction history")

            val mempoolCleared = mempoolRepo.delete(session, block)
            if (!mempoolCleared) throw RuntimeException("failed to clear approved transactions from mempool")

            session.commitTransaction()

            logger.ccInfo(
                LogComponent.DATABASE,
                "successfully accepted new block",
                "height" to block.height
            )
            true
        } catch (e: Exception) {
            //失敗でロールバック
            session.abortTransaction()
            //競合(コンマの差で負け)か?
            val isWriteConflict = e is MongoCommandException && e.errorCode == 112
            if (isWriteConflict) {
                logger.ccInfo(
                    LogComponent.DATABASE,
                    "write conflict detected (block already handled by another process)",
                    "height" to block.height
                )
            } else {
                logger.ccWarning(
                    LogComponent.DATABASE,
                    "failed to accept new block",
                    e,
                    "height" to block.height
                )
            }
            false
        } finally {
            session.close()
        }
    }
}