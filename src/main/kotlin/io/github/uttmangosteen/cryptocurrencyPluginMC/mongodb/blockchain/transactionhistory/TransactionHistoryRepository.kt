package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.transactionhistory

import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
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
                val sender = tx.inputs.firstOrNull()?.publicKey // 簡易的に最初のインプットの公開鍵を送信者とする場合
                tx.outputs.map { output ->
                    Document().apply {
                        // _idは「txHash + 宛先」で重複防止
                        append("_id", "${tx.txHash}_${output.receiverPubKey}")
                        append("txHash", tx.txHash)
                        append("senderPubKey", sender)
                        append("receiverPubKey", output.receiverPubKey)
                        append("amount", output.amount)
                        append("height", height)
                        append("blockTimestamp", blockTimestamp)
                        append("txTimestamp", tx.timestamp)
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
}