package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain

import com.mongodb.MongoCommandException
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.CoinbasePolicy
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.DifficultyPolicy
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Signer
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
    // blockchain積み上げ、使ったutxo変換、確定分history記述、確定txをmempoolから削除
    suspend fun acceptNewBlock(block: Block): Boolean {
        val session = provider.startSession()
        return try {
            session.startTransaction()

            val latestBlock = blockRepo.getLatestBlock(session) ?: return false
            val expectedDifficulty = DifficultyPolicy.calculateExpectedDifficulty(latestBlock)

            if (!block.isValid(latestBlock, expectedDifficulty)) return false

            var totalFees = 0L

            for (i in 1 until block.transactions.size) {
                val tx = block.transactions[i]
                var inputAmountSum = 0L

                for (input in tx.inputs) {
                    if (Signer.normalizePublicKey(input.publicKey) == null) return false
                    val utxo = utxoRepo.findUtxo(session, input.prevTxHash, input.outputIndex) ?: return false
                    if (input.publicKey != utxo.receiverPubKey) return false
                    inputAmountSum = Math.addExact(inputAmountSum, utxo.amount)
                }

                for (output in tx.outputs) {
                    if (Signer.normalizePublicKey(output.receiverPubKey) == null) return false
                }

                val outputAmountSum = tx.outputs.fold(0L) { sum, output ->
                    Math.addExact(sum, output.amount)
                }

                if (inputAmountSum < outputAmountSum) return false

                val txFee = inputAmountSum - outputAmountSum
                totalFees = Math.addExact(totalFees, txFee)
            }

            val coinbaseTx = block.transactions[0]
            val coinbaseOutput = coinbaseTx.outputs.singleOrNull() ?: return false

            if (Signer.normalizePublicKey(coinbaseOutput.receiverPubKey) == null) return false

            val expectedCoinbaseAmount = CoinbasePolicy.calculateCoinbaseAmount(totalFees = totalFees)

            if (coinbaseOutput.amount != expectedCoinbaseAmount) return false

            val blockSaved = blockRepo.saveBlock(session, block)
            if (!blockSaved) return false

            val utxoApplied = utxoRepo.applyTransactions(session, block.transactions)
            if (!utxoApplied) return false

            val historyWritten = historyRepo.writeHistory(session, block.transactions, block.height, block.timestamp)
            if (!historyWritten) return false

            val mempoolCleared = mempoolRepo.delete(session, block)
            if (!mempoolCleared) return false

            session.commitTransaction()

            logger.ccInfo(
                LogComponent.DATABASE,
                "successfully accepted new block",
                "height" to block.height,
                "txCount" to block.transactions.size,
                "totalFees" to totalFees,
                "coinbaseAmount" to coinbaseOutput.amount,
                "difficulty" to block.difficulty
            )
            true
        } catch (e: Exception) {
            session.abortTransaction()

            val isWriteConflict = e is MongoCommandException && e.errorCode == 112
            if (isWriteConflict) {
                logger.ccInfo(
                    LogComponent.DATABASE,
                    "write conflict detected while accepting block",
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