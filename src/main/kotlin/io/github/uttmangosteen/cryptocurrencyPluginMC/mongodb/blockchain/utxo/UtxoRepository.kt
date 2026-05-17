package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.utxo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.InsertOneModel
import com.mongodb.client.model.WriteModel
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.OutPoint
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Utxo
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toDocument
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toOutPointId
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toUtxo
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.util.logging.Logger

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

    //今送金に使える残高取得用
    suspend fun getAvailableUtxos(publicKey: String): List<Utxo> {
        return try {
            collection.find(
                Filters.and(
                    Filters.eq("receiverPubKey", publicKey),
                    Filters.or(
                        Filters.exists("lockedByTxId", false),
                        Filters.eq("lockedByTxId", null)
                    )
                )
            )
                .map { it.toUtxo() }
                .toList()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.UTXO_REPOSITORY,
                "failed to get available utxos",
                e,
                "publicKey" to publicKey
            )
            emptyList()
        }
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