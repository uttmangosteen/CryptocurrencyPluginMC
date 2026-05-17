package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.utxo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.InsertOneModel
import com.mongodb.client.model.WriteModel
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.OutPoint
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Utxo
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toDocument
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toMongoId
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toUtxo
import io.github.uttmangosteen.cryptocurrencyPluginMC.util.toHex
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
            Indexes.ascending("receiverPubKeyHex")
        )

        collection.createIndex(
            Indexes.ascending("lockedByTxId")
        )

        logger.ccInfo(LogComponent.UTXO_REPOSITORY, "setup completed")
    }

    //今送金に使える残高取得用
    suspend fun getAvailableUtxos(publicKeyHex: String): List<Utxo> {
        return try {
            collection.find(
                Filters.and(
                    Filters.eq("receiverPubKeyHex", publicKeyHex),
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
                "publicKeyHex" to publicKeyHex
            )
            emptyList()
        }
    }

    //送金作成時Transactionから幾つかのutxoロック
    suspend fun lock(transaction: Transaction): Boolean {
        if (transaction.isCoinbase || transaction.inputs.isEmpty()) return true

        val txId = transaction.txHash.toHex()
        val outPoints = transaction.toSpentOutPoints()
        val mongoIds = outPoints.map { outPoint -> outPoint.toMongoId() }

        return try {
            val result = collection.updateMany(
                Filters.and(
                    Filters.`in`("_id", mongoIds),
                    Filters.or(
                        Filters.exists("lockedByTxId", false),
                        Filters.eq("lockedByTxId", null)
                    )
                ),
                Updates.set("lockedByTxId", txId)
            )
            result.wasAcknowledged() && result.modifiedCount == mongoIds.size.toLong()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.UTXO_REPOSITORY,
                "failed to lock utxos for transaction",
                e,
                "txId" to txId,
                "requestedCount" to mongoIds.size
            )
            false
        }
    }

    //mempoolからtransaction消すときtransaction内のutxoロック解除
    suspend fun unlock(transaction: Transaction): Boolean {
        val txId = transaction.txHash.toHex()
        return try {
            val result = collection.updateMany(
                Filters.eq("lockedByTxId", txId),
                Updates.unset("lockedByTxId")
            )
            result.wasAcknowledged()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.UTXO_REPOSITORY,
                "failed to unlock utxos for transaction",
                e,
                "txId" to txId
            )
            false
        }
    }

    //Block承認による消費Utxo削除と新規Utxo追加
    suspend fun applyTransactions(transactions: List<Transaction>): Boolean {
        if (transactions.isEmpty()) return true

        val allSpentOutPoints = transactions.flatMap { it.toSpentOutPoints() }
        val allCreatedUtxos = transactions.flatMap { it.toCreatedUtxos() }

        return try {
            val deleteResult = if (allSpentOutPoints.isEmpty()) {
                true
            } else {
                val result = collection.deleteMany(
                    Filters.`in`("_id", allSpentOutPoints.map { it.toMongoId() })
                )
                result.wasAcknowledged()
            }

            if (!deleteResult) return false
            if (allCreatedUtxos.isEmpty()) return true

            val insertResult = collection.bulkWrite(
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
                txHashHex = input.prevTxHash.toHex(),
                outputIndex = input.outputIndex
            )
        }
    }

    private fun Transaction.toCreatedUtxos(): List<Utxo> {
        val txHashHex = txHash.toHex()

        return outputs.mapIndexed { index, output ->
            val receiverPubKeyHex = output.receiverPubKey.toHex()

            Utxo(
                outPoint = OutPoint(
                    txHashHex = txHashHex,
                    outputIndex = index
                ),
                txHash = txHash,
                amount = output.amount,
                receiverPubKey = output.receiverPubKey,
                receiverPubKeyHex = receiverPubKeyHex
            )
        }
    }
}