package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain

import com.mongodb.MongoCommandException
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.OutPoint
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.CoinbasePolicy
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.DifficultyPolicy
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Signer
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.MongoDatabaseProvider
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.MongoTransactionOutcome
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.MongoTransactionRunner
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.repository.BlockRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.repository.MempoolRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.repository.UtxoRepository
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.repository.TransactionHistoryRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import java.util.logging.Logger

class BlockchainManager(
    provider: MongoDatabaseProvider,
    private val blockRepo: BlockRepository,
    private val utxoRepo: UtxoRepository,
    private val historyRepo: TransactionHistoryRepository,
    private val mempoolRepo: MempoolRepository,
    private val miningDelayTicks: Int,
    private val logger: Logger
) {
    private val transactionRunner = MongoTransactionRunner(provider, logger)

    // blockchain積み上げ、使ったutxo変換、確定分history記述、確定txをmempoolから削除
    suspend fun acceptNewBlock(block: Block): Boolean {
        val result = transactionRunner.run<BlockAcceptanceResult>(
            operation = "failed to accept new block",
            block = { session ->
                val details = acceptNewBlockInTransaction(session, block)
                if (details != null) {
                    MongoTransactionOutcome.Commit(BlockAcceptanceResult.Accepted(details))
                } else {
                    MongoTransactionOutcome.Abort(BlockAcceptanceResult.Rejected)
                }
            },
            onFailure = { error ->
                val isWriteConflict = error is MongoCommandException && error.errorCode == 112
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
                        error,
                        "height" to block.height
                    )
                }
            }
        ) ?: return false

        val accepted = result as? BlockAcceptanceResult.Accepted ?: return false
        mempoolRepo.invalidateMiningSelections()
        logger.ccInfo(
            LogComponent.DATABASE,
            "successfully accepted new block",
            "height" to block.height,
            "txCount" to block.transactions.size,
            "totalFees" to accepted.details.totalFees,
            "coinbaseAmount" to accepted.details.coinbaseAmount,
            "mintedReward" to accepted.details.mintedReward,
            "totalChainSupply" to block.totalChainSupply,
            "networkMiningPower" to block.networkMiningPower,
            "difficulty" to block.difficulty
        )
        return true
    }

    private suspend fun acceptNewBlockInTransaction(
        session: ClientSession,
        block: Block
    ): AcceptedBlockDetails? {
        val latestBlock = blockRepo.getLatestBlock(session) ?: return null
        val expectedDifficulty = DifficultyPolicy.calculateExpectedDifficulty(
            networkMiningPower = block.networkMiningPower,
            miningDelayTicks = miningDelayTicks
        )

        if (!block.isValid(latestBlock, expectedDifficulty)) return null

        val allInputOutPoints = block.transactions
            .filter { !it.isCoinbase }
            .flatMap { tx ->
                tx.inputs.map { input ->
                    OutPoint(
                        txHash = input.prevTxHash,
                        outputIndex = input.outputIndex
                    )
                }
            }

        val resolvedInputUtxos = utxoRepo.findUtxos(
            session = session,
            outPoints = allInputOutPoints
        )

        if (resolvedInputUtxos.size != allInputOutPoints.distinct().size) return null

        var totalFees = 0L

        for (i in 1 until block.transactions.size) {
            val tx = block.transactions[i]
            var inputAmountSum = 0L

            for (input in tx.inputs) {
                if (Signer.normalizePublicKey(input.publicKey) == null) return null

                val outPoint = OutPoint(
                    txHash = input.prevTxHash,
                    outputIndex = input.outputIndex
                )

                val utxo = resolvedInputUtxos[outPoint] ?: return null
                if (input.publicKey != utxo.receiverPubKey) return null
                inputAmountSum = Math.addExact(inputAmountSum, utxo.amount)
            }

            for ((_, receiverPubKey) in tx.outputs) {
                if (Signer.normalizePublicKey(receiverPubKey) == null) return null
            }

            val outputAmountSum = tx.outputs.fold(0L) { sum, output ->
                Math.addExact(sum, output.amount)
            }

            if (inputAmountSum < outputAmountSum) return null

            val txFee = inputAmountSum - outputAmountSum
            totalFees = Math.addExact(totalFees, txFee)
        }

        val coinbaseTx = block.transactions[0]
        val coinbaseOutput = coinbaseTx.outputs.singleOrNull() ?: return null

        if (Signer.normalizePublicKey(coinbaseOutput.receiverPubKey) == null) return null

        val mintedReward = CoinbasePolicy.calculateMintedReward(
            blockHeight = block.height,
            currentSupply = latestBlock.totalChainSupply
        )

        val expectedCoinbaseAmount = CoinbasePolicy.calculateCoinbaseAmount(
            blockHeight = block.height,
            currentSupply = latestBlock.totalChainSupply,
            totalFees = totalFees
        )

        if (coinbaseOutput.amount != expectedCoinbaseAmount) return null

        val expectedTotalChainSupply = Math.addExact(latestBlock.totalChainSupply, mintedReward)
        if (block.totalChainSupply != expectedTotalChainSupply) return null

        val blockSaved = blockRepo.saveBlock(session, block)
        if (!blockSaved) return null

        val utxoApplied = utxoRepo.applyTransactions(session, block.transactions)
        if (!utxoApplied) return null

        val historyWritten = historyRepo.writeHistory(
            session = session,
            transactions = block.transactions,
            resolvedInputUtxos = resolvedInputUtxos,
            height = block.height,
            blockTimestamp = block.timestamp
        )
        if (!historyWritten) return null

        val mempoolCleared = mempoolRepo.delete(session, block)
        if (!mempoolCleared) return null

        return AcceptedBlockDetails(
            totalFees = totalFees,
            coinbaseAmount = coinbaseOutput.amount,
            mintedReward = mintedReward
        )
    }

    private sealed interface BlockAcceptanceResult {
        data class Accepted(val details: AcceptedBlockDetails) : BlockAcceptanceResult
        data object Rejected : BlockAcceptanceResult
    }

    private data class AcceptedBlockDetails(
        val totalFees: Long,
        val coinbaseAmount: Long,
        val mintedReward: Long
    )
}
