package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.ClientSession
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.OutPoint
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Utxo
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.CoinbasePolicy
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.DifficultyPolicy
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Signer
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toBlock
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toDocument
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.util.logging.Logger

class DatabaseMaintenanceService(
    private val provider: MongoDatabaseProvider,
    private val miningDelayTicks: Int,
    private val logger: Logger
) {
    private val database = provider.database

    private val blocks = database.getCollection<Document>("blocks")
    private val mempool = database.getCollection<Document>("mempool")
    private val utxos = database.getCollection<Document>("utxos")
    private val transactionHistory = database.getCollection<Document>("transaction_history")
    private val miningMachines = database.getCollection<Document>("mining_machines")

    // =========================================================================
    // Helper Methods for Refactoring
    // =========================================================================

    private suspend fun <T> withTransaction(
        errorMessage: String,
        block: suspend (ClientSession) -> T
    ): T? {
        val session = provider.startSession()
        return try {
            session.startTransaction()
            val result = block(session)
            session.commitTransaction()
            result
        } catch (e: Exception) {
            session.abortTransaction()
            logger.ccWarning(LogComponent.DATABASE, errorMessage, e)
            null
        } finally {
            session.close()
        }
    }

    private suspend fun clearAndInsertCaches(
        session: ClientSession,
        utxoList: List<Document> = emptyList(),
        histories: List<Document> = emptyList(),
        clearMiningMachines: Boolean = true
    ) {
        mempool.deleteMany(session, Document())
        utxos.deleteMany(session, Document())
        transactionHistory.deleteMany(session, Document())

        if (clearMiningMachines) {
            miningMachines.updateMany(
                session,
                Document(),
                Document(
                    $$"$set",
                    Document("enabled", false)
                        .append("status", "DISABLED")
                        .append("miningBlock", null)
                )
            )
        }

        if (utxoList.isNotEmpty()) {
            utxos.insertMany(session, utxoList)
        }

        if (histories.isNotEmpty()) {
            transactionHistory.insertMany(session, histories)
        }
    }

    private fun Block.extractNonCoinbaseInputOutPoints(): List<OutPoint> {
        return transactions
            .filter { !it.isCoinbase }
            .flatMap { tx ->
                tx.inputs.map { input ->
                    OutPoint(
                        txHash = input.prevTxHash,
                        outputIndex = input.outputIndex
                    )
                }
            }
    }

    private fun updateUtxoMap(utxoMap: MutableMap<OutPoint, Utxo>, block: Block, consumedOutPoints: List<OutPoint>) {
        consumedOutPoints.forEach { utxoMap.remove(it) }
        block.transactions.forEach { tx ->
            tx.outputs.forEachIndexed { outputIndex, output ->
                val outPoint = OutPoint(txHash = tx.txHash, outputIndex = outputIndex)
                utxoMap[outPoint] = Utxo(
                    outPoint = outPoint,
                    amount = output.amount,
                    receiverPubKey = output.receiverPubKey,
                    lockedByTxId = null
                )
            }
        }
    }

    // =========================================================================
    // Main Service Methods
    // =========================================================================

    suspend fun flushCaches(): Boolean {
        val success = withTransaction("database cache flush failed") { session ->
            clearAndInsertCaches(session, clearMiningMachines = true)
            true
        } ?: false

        if (success) {
            logger.ccInfo(LogComponent.DATABASE, "database cache flush completed")
        }
        return success
    }

    suspend fun rebuildCachesFromBlocks(): RebuildResult {
        val storedBlocks = loadBlocks()
            ?: return RebuildResult.Failed("blocks の読み込みに失敗しました")

        if (storedBlocks.isEmpty()) {
            return RebuildResult.Failed("blocks が空です")
        }

        val buildResult = buildCachesTrustingBlocks(storedBlocks)
        if (buildResult !is CacheBuildResult.Success) {
            return RebuildResult.Failed((buildResult as CacheBuildResult.Failed).message)
        }

        val saved = replaceCaches(
            utxoList = buildResult.utxos,
            histories = buildResult.histories,
            clearMiningMachines = true
        )

        if (!saved) {
            return RebuildResult.Failed("再構築したデータの保存に失敗しました")
        }

        logger.ccInfo(
            LogComponent.DATABASE,
            "database cache rebuild completed without strict block verification",
            "blockCount" to storedBlocks.size,
            "utxoCount" to buildResult.utxos.size,
            "historyCount" to buildResult.histories.size
        )

        return RebuildResult.Success(
            blockCount = storedBlocks.size,
            utxoCount = buildResult.utxos.size,
            historyCount = buildResult.histories.size
        )
    }

    suspend fun verifyBlocks(): VerifyResult {
        val storedBlocks = loadBlocks()
            ?: return VerifyResult.Invalid(invalidHeight = -1, message = "blocks の読み込みに失敗しました")

        return when (val result = verifyBlockList(storedBlocks)) {
            is ChainVerifyResult.Valid -> VerifyResult.Valid(blockCount = storedBlocks.size)
            is ChainVerifyResult.Invalid -> VerifyResult.Invalid(invalidHeight = result.invalidHeight, message = result.message)
        }
    }

    suspend fun pruneInvalidChainAndRebuild(): PruneResult {
        val storedBlocks = loadBlocks()
            ?: return PruneResult.Failed("blocks の読み込みに失敗しました")

        if (storedBlocks.isEmpty()) {
            return PruneResult.Failed("blocks が空です")
        }

        val verifyResult = verifyBlockList(storedBlocks)

        val validBlocks = when (verifyResult) {
            is ChainVerifyResult.Valid -> storedBlocks
            is ChainVerifyResult.Invalid -> {
                if (verifyResult.invalidHeight <= 0) {
                    return PruneResult.Failed("genesis block が不正なため自動修復できません: ${verifyResult.message}")
                }
                storedBlocks.filter { it.height < verifyResult.invalidHeight }
            }
        }

        val buildResult = buildCachesTrustingBlocks(validBlocks)
        if (buildResult !is CacheBuildResult.Success) {
            return PruneResult.Failed((buildResult as CacheBuildResult.Failed).message)
        }

        val deletedBlocksCount = withTransaction("failed to prune invalid chain") { session ->
            val deletedCount = if (verifyResult is ChainVerifyResult.Invalid) {
                blocks.deleteMany(session, Filters.gte("_id", verifyResult.invalidHeight)).deletedCount
            } else {
                0L
            }

            clearAndInsertCaches(
                session = session,
                utxoList = buildResult.utxos.map { it.toDocument() },
                histories = buildResult.histories,
                clearMiningMachines = true
            )
            deletedCount
        } ?: return PruneResult.Failed("不正チェーンの刈り取りに失敗しました")

        return when (verifyResult) {
            is ChainVerifyResult.Valid -> PruneResult.NoInvalidBlock(
                blockCount = validBlocks.size,
                utxoCount = buildResult.utxos.size,
                historyCount = buildResult.histories.size
            )
            is ChainVerifyResult.Invalid -> PruneResult.Pruned(
                prunedFromHeight = verifyResult.invalidHeight,
                deletedBlocks = deletedBlocksCount,
                remainingBlocks = validBlocks.size,
                utxoCount = buildResult.utxos.size,
                historyCount = buildResult.histories.size
            )
        }
    }

    private suspend fun loadBlocks(): List<Block>? {
        return try {
            blocks.find()
                .sort(Sorts.ascending("_id"))
                .map { it.toBlock() }
                .toList()
        } catch (e: Exception) {
            logger.ccWarning(LogComponent.DATABASE, "failed to load blocks", e)
            null
        }
    }

    private suspend fun replaceCaches(
        utxoList: List<Utxo>,
        histories: List<Document>,
        clearMiningMachines: Boolean
    ): Boolean {
        return withTransaction("failed to replace database caches") { session ->
            clearAndInsertCaches(
                session = session,
                utxoList = utxoList.map { it.toDocument() },
                histories = histories,
                clearMiningMachines = clearMiningMachines
            )
            true
        } ?: false
    }

    private fun buildCachesTrustingBlocks(blocks: List<Block>): CacheBuildResult {
        val utxoMap = linkedMapOf<OutPoint, Utxo>()
        val histories = mutableListOf<Document>()

        for (block in blocks) {
            val inputOutPoints = block.extractNonCoinbaseInputOutPoints()

            val resolvedInputUtxos = inputOutPoints.associateWith { outPoint ->
                utxoMap[outPoint] ?: return CacheBuildResult.Failed("height ${block.height}: cache再構築に必要な入力UTXOが見つかりません")
            }

            updateUtxoMap(utxoMap, block, inputOutPoints)

            histories += block.transactions.map { tx ->
                val inputUtxos = tx.inputs.mapNotNull { input ->
                    resolvedInputUtxos[OutPoint(txHash = input.prevTxHash, outputIndex = input.outputIndex)]
                }

                val inputAmount = inputUtxos.fold(0L) { sum, utxo -> Math.addExact(sum, utxo.amount) }
                val outputAmount = tx.outputs.fold(0L) { sum, output -> Math.addExact(sum, output.amount) }
                val fee = if (tx.isCoinbase) 0L else inputAmount - outputAmount

                val relatedPubKeys = (
                        tx.inputs.map { it.publicKey } +
                                tx.outputs.map { it.receiverPubKey } +
                                inputUtxos.map { it.receiverPubKey }
                        ).distinct()

                Document("_id", tx.txHash)
                    .append("txHash", tx.txHash)
                    .append("transaction", tx.toDocument())
                    .append("inputUtxos", inputUtxos.map { it.toDocument() })
                    .append("fee", fee)
                    .append("height", block.height)
                    .append("blockTimestamp", block.timestamp)
                    .append("txTimestamp", tx.timestamp)
                    .append("memo", tx.memo)
                    .append("relatedPubKeys", relatedPubKeys)
            }
        }

        return CacheBuildResult.Success(
            utxos = utxoMap.values.toList(),
            histories = histories
        )
    }

    private fun verifyBlockList(blocks: List<Block>): ChainVerifyResult {
        if (blocks.isEmpty()) return ChainVerifyResult.Invalid(invalidHeight = -1, message = "blocks が空です")

        val genesis = blocks.first()
        if (genesis.height != 0) return ChainVerifyResult.Invalid(invalidHeight = genesis.height, message = "genesis block が見つかりません")
        if (genesis.hash == null) return ChainVerifyResult.Invalid(invalidHeight = 0, message = "genesis block の hash が null です")

        val utxoMap = linkedMapOf<OutPoint, Utxo>()
        var latestBlock = genesis

        for (blockIndex in 1 until blocks.size) {
            val block = blocks[blockIndex]

            if (block.height != latestBlock.height + 1) {
                return ChainVerifyResult.Invalid(invalidHeight = block.height, message = "ブロック高が連続していません")
            }

            val expectedDifficulty = DifficultyPolicy.calculateExpectedDifficulty(
                networkMiningPower = block.networkMiningPower,
                miningDelayTicks = miningDelayTicks
            )

            if (!block.isValid(latestBlock, expectedDifficulty)) {
                return ChainVerifyResult.Invalid(invalidHeight = block.height, message = "ブロック自体の検証に失敗しました")
            }

            val allInputOutPoints = block.extractNonCoinbaseInputOutPoints()
            val resolvedInputUtxos = allInputOutPoints.associateWith { outPoint ->
                utxoMap[outPoint] ?: return ChainVerifyResult.Invalid(invalidHeight = block.height, message = "存在しない、または使用済みのUTXOを参照しています")
            }

            var totalFees = 0L

            for (txIndex in 1 until block.transactions.size) {
                val tx = block.transactions[txIndex]

                if (!tx.isValid()) {
                    return ChainVerifyResult.Invalid(invalidHeight = block.height, message = "不正な transaction があります: ${tx.txHash}")
                }

                var inputAmount = 0L
                for (input in tx.inputs) {
                    if (Signer.normalizePublicKey(input.publicKey) == null) {
                        return ChainVerifyResult.Invalid(invalidHeight = block.height, message = "入力公開鍵が不正です")
                    }

                    val utxo = resolvedInputUtxos[OutPoint(txHash = input.prevTxHash, outputIndex = input.outputIndex)]
                        ?: return ChainVerifyResult.Invalid(invalidHeight = block.height, message = "入力UTXOが見つかりません")

                    if (input.publicKey != utxo.receiverPubKey) {
                        return ChainVerifyResult.Invalid(invalidHeight = block.height, message = "UTXO所有者と入力公開鍵が一致しません")
                    }

                    inputAmount = Math.addExact(inputAmount, utxo.amount)
                }

                val outputAmount = tx.outputs.fold(0L) { sum, output ->
                    if (Signer.normalizePublicKey(output.receiverPubKey) == null) {
                        return ChainVerifyResult.Invalid(invalidHeight = block.height, message = "出力公開鍵が不正です")
                    }
                    Math.addExact(sum, output.amount)
                }

                if (inputAmount < outputAmount) {
                    return ChainVerifyResult.Invalid(invalidHeight = block.height, message = "入力額より出力額が大きい transaction があります")
                }

                totalFees = Math.addExact(totalFees, inputAmount - outputAmount)
            }

            val coinbaseTx = block.transactions.firstOrNull()
                ?: return ChainVerifyResult.Invalid(invalidHeight = block.height, message = "coinbase transaction がありません")

            val coinbaseOutput = coinbaseTx.outputs.singleOrNull()
                ?: return ChainVerifyResult.Invalid(invalidHeight = block.height, message = "coinbase output が不正です")

            val expectedCoinbaseAmount = CoinbasePolicy.calculateCoinbaseAmount(
                blockHeight = block.height,
                currentSupply = latestBlock.totalChainSupply,
                totalFees = totalFees
            )

            if (coinbaseOutput.amount != expectedCoinbaseAmount) {
                return ChainVerifyResult.Invalid(invalidHeight = block.height, message = "coinbase 報酬額が不正です")
            }

            val expectedMintedReward = CoinbasePolicy.calculateMintedReward(
                blockHeight = block.height,
                currentSupply = latestBlock.totalChainSupply
            )

            val expectedSupply = Math.addExact(latestBlock.totalChainSupply, expectedMintedReward)
            if (block.totalChainSupply != expectedSupply) {
                return ChainVerifyResult.Invalid(invalidHeight = block.height, message = "totalChainSupply が不正です")
            }

            updateUtxoMap(utxoMap, block, allInputOutPoints)

            latestBlock = block
        }

        return ChainVerifyResult.Valid
    }

    // =========================================================================
    // Sealed Classes (Unchanged)
    // =========================================================================

    private sealed class ChainVerifyResult {
        data object Valid : ChainVerifyResult()
        data class Invalid(val invalidHeight: Int, val message: String) : ChainVerifyResult()
    }

    sealed class VerifyResult {
        data class Valid(val blockCount: Int) : VerifyResult()
        data class Invalid(val invalidHeight: Int, val message: String) : VerifyResult()
    }

    sealed class PruneResult {
        data class NoInvalidBlock(val blockCount: Int, val utxoCount: Int, val historyCount: Int) : PruneResult()
        data class Pruned(val prunedFromHeight: Int, val deletedBlocks: Long, val remainingBlocks: Int, val utxoCount: Int, val historyCount: Int) : PruneResult()
        data class Failed(val message: String) : PruneResult()
    }

    private sealed class CacheBuildResult {
        data class Success(val utxos: List<Utxo>, val histories: List<Document>) : CacheBuildResult()
        data class Failed(val message: String) : CacheBuildResult()
    }

    sealed class RebuildResult {
        data class Success(val blockCount: Int, val utxoCount: Int, val historyCount: Int) : RebuildResult()
        data class Failed(val message: String) : RebuildResult()
    }
}