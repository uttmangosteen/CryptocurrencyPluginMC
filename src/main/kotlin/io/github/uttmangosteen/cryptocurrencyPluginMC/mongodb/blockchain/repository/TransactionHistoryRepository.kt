package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.util.logging.Logger

class TransactionHistoryRepository(
    database: MongoDatabase,
    private val logger: Logger
) {
    private val collection = database.getCollection<Document>("transaction_history")

    suspend fun setup() {
        collection.createIndex(Indexes.ascending("senderPubKey"))
        collection.createIndex(Indexes.ascending("receiverPubKey"))
        logger.ccInfo(LogComponent.TRANSACTION_HISTORY_REPOSITORY, "setup completed")
    }

    //acceptNewBlock時実行
    suspend fun writeHistory(session: ClientSession, transactions: List<Transaction>, height: Int, blockTimestamp: Long): Boolean {
        if (transactions.isEmpty()) return true
        try {

            val documents = transactions.flatMap { tx ->
                val sender = tx.inputs.firstOrNull()?.publicKey
                tx.outputs.mapIndexed { outputIndex, output ->
                    val historyId = toHistoryId(tx.txHash, outputIndex)
                    Document().apply {
                        append("_id", historyId)
                        append("txHash", tx.txHash)
                        append("outputIndex", outputIndex)
                        append("senderPubKey", sender)
                        append("receiverPubKey", output.receiverPubKey)
                        append("amount", output.amount)
                        append("height", height)
                        append("blockTimestamp", blockTimestamp)
                        append("txTimestamp", tx.timestamp)
                        append("memo", tx.memo)
                    }
                }
            }

            if (documents.isEmpty()) return true
            val result = collection.insertMany(session, documents)
            return result.wasAcknowledged()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.TRANSACTION_HISTORY_REPOSITORY,
                "failed to write transaction history",
                e,
                "height" to height
            )
            return false
        }
    }

    suspend fun getHistory(pubKey: String): List<Document> {
        return collection.find(
            Filters.or(
                Filters.eq("senderPubKey", pubKey),
                Filters.eq("receiverPubKey", pubKey)
            )
        )
            .sort(Sorts.descending("blockTimestamp", "txTimestamp"))
            .toList()
    }

    private fun toHistoryId(txHash: String, outputIndex: Int): String {
        return "${txHash}_$outputIndex"
    }
}