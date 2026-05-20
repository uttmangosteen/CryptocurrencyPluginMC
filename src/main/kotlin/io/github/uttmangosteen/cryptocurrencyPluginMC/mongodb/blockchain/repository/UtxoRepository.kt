package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.InsertOneModel
import com.mongodb.client.model.Updates
import com.mongodb.client.model.WriteModel
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.OutPoint
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Utxo
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.model.WalletState
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toDocument
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toOutPointId
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toUtxo
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.lang.Math.addExact
import java.util.logging.Logger
import kotlin.collections.map

class UtxoRepository(
    database: MongoDatabase,
    private val logger: Logger
) {
    private val collection = database.getCollection<Document>("utxos")

    suspend fun setup() {
        collection.createIndex(
            Indexes.ascending("receiverPubKey")
        )

        collection.createIndex(
            Indexes.ascending("lockedByTxId")
        )

        logger.ccInfo(LogComponent.UTXO_REPOSITORY, "setup completed")
    }

    private suspend fun getAllUtxos(publicKey: String): List<Utxo> {
        return try {
            collection.find(Filters.eq("receiverPubKey", publicKey))
                .map { it.toUtxo() }
                .toList()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.UTXO_REPOSITORY,
                "failed to get utxos",
                e,
                "publicKey" to publicKey
            )
            emptyList()
        }
    }

    private fun List<Utxo>.sumAmounts(): Long {
        return this.fold(0L) { sum, utxo -> addExact(sum, utxo.amount) }
    }

    suspend fun getAvailableUtxos(publicKey: String): List<Utxo> {
        return getAllUtxos(publicKey).filter { it.lockedByTxId == null }
    }

    //balance用
    suspend fun getWalletState(publicKey: String): WalletState {
        val allUtxos = getAllUtxos(publicKey)
        val (pending, available) = allUtxos.partition { it.lockedByTxId != null }

        val balance = try { available.sumAmounts() } catch (_: ArithmeticException) { 0L }
        val pendingBalance = try { pending.sumAmounts() } catch (_: ArithmeticException) { 0L }
        val totalBalance = try { addExact(balance, pendingBalance) } catch (_: ArithmeticException) { 0L }

        return WalletState(
            availableUtxos = available,
            pendingUtxos = pending,
            balance = balance,
            pendingBalance = pendingBalance,
            totalBalance = totalBalance
        )
    }


    //送金作成時Transactionが使用するutxoをロック
    suspend fun lock(transaction: Transaction): Boolean {
        if (transaction.isCoinbase || transaction.inputs.isEmpty()) return true

        val outPoints = transaction.toSpentOutPoints()
        val outPointIds = outPoints.map { outPoint -> outPoint.toOutPointId() }

        return try {
            val result = collection.updateMany(
                Filters.and(
                    Filters.`in`("_id", outPointIds),
                    Filters.or(
                        Filters.exists("lockedByTxId", false),
                        Filters.eq("lockedByTxId", null)
                    )
                ),
                Updates.set("lockedByTxId", transaction.txHash)
            )
            result.wasAcknowledged() && result.modifiedCount == outPointIds.size.toLong()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.UTXO_REPOSITORY,
                "failed to lock utxos for transaction",
                e,
                "txId" to transaction.txHash,
                "requestedCount" to outPointIds.size
            )
            false
        }
    }

    //mempoolからtransaction消すときtransaction内のutxoロック解除
    suspend fun unlock(transaction: Transaction): Boolean {
        return try {
            val result = collection.updateMany(
                Filters.eq("lockedByTxId", transaction.txHash),
                Updates.unset("lockedByTxId")
            )
            result.wasAcknowledged()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.UTXO_REPOSITORY,
                "failed to unlock utxos for transaction",
                e,
                "txId" to transaction.txHash
            )
            false
        }
    }

    //acceptNewBlock時実行
    suspend fun applyTransactions(session: ClientSession, transactions: List<Transaction>): Boolean {
        if (transactions.isEmpty()) return true

        val allSpentOutPoints = transactions.flatMap { it.toSpentOutPoints() }
        val allCreatedUtxos = transactions.flatMap { it.toCreatedUtxos() }

        return try {
            val deleteResult = if (allSpentOutPoints.isEmpty()) {
                true
            } else {
                val result = collection.deleteMany(
                    session,
                    Filters.`in`("_id", allSpentOutPoints.map { it.toOutPointId() })
                )
                result.wasAcknowledged() && result.deletedCount == allSpentOutPoints.size.toLong()
            }

            if (!deleteResult) return false
            if (allCreatedUtxos.isEmpty()) return true

            val insertResult = collection.bulkWrite(
                session,
                allCreatedUtxos.map { utxo ->
                    InsertOneModel(utxo.toDocument()) as WriteModel<Document>
                }
            )

            insertResult.wasAcknowledged()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.UTXO_REPOSITORY,
                "failed to apply block transactions",
                e,
                "transactionCount" to transactions.size
            )
            false
        }
    }

    suspend fun findUtxo(session: ClientSession, prevTxHash: String, outputIndex: Int): Utxo? {
        return try {
            val outPointId = OutPoint(
                txHash = prevTxHash,
                outputIndex = outputIndex
            ).toOutPointId()

            collection.find(session, Filters.eq("_id", outPointId))
                .firstOrNull()
                ?.toUtxo()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.UTXO_REPOSITORY,
                "failed to find utxo",
                e,
                "prevTxHash" to prevTxHash,
                "outputIndex" to outputIndex
            )
            null
        }
    }

    suspend fun findUtxos(
        session: ClientSession? = null,
        outPoints: Collection<OutPoint>
    ): Map<OutPoint, Utxo> {
        if (outPoints.isEmpty()) return emptyMap()

        val outPointIds = outPoints.map { it.toOutPointId() }

        return try {
            val findPublisher = if (session != null) {
                collection.find(session, Filters.`in`("_id", outPointIds))
            } else {
                collection.find(Filters.`in`("_id", outPointIds))
            }

            findPublisher
                .map { it.toUtxo() }
                .toList()
                .associateBy { it.outPoint }
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.UTXO_REPOSITORY,
                "failed to find utxos",
                e,
                "requestedCount" to outPoints.size
            )
            emptyMap()
        }
    }

    private fun Transaction.toSpentOutPoints(): List<OutPoint> {
        if (isCoinbase) return emptyList()

        return inputs.map { input ->
            OutPoint(
                txHash = input.prevTxHash,
                outputIndex = input.outputIndex
            )
        }
    }

    private fun Transaction.toCreatedUtxos(): List<Utxo> {
        return outputs.mapIndexed { index, output ->
            Utxo(
                outPoint = OutPoint(
                    txHash = txHash,
                    outputIndex = index
                ),
                amount = output.amount,
                receiverPubKey = output.receiverPubKey,
                lockedByTxId = null
            )
        }
    }
}