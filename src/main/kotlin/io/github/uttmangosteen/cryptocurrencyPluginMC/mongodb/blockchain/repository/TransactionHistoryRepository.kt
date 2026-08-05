package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.OutPoint
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Utxo
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.model.TransactionHistoryEntry
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toDocument
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toTransaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toUtxo
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.util.logging.Logger

class TransactionHistoryRepository(
    database: MongoDatabase,
    private val logger: Logger
) {
    private val collection = database.getCollection<Document>("transaction_history")

    suspend fun setup() {
        collection.createIndex(Indexes.ascending("relatedPubKeys"))
        collection.createIndex(Indexes.descending("blockTimestamp", "txTimestamp"))
        logger.ccInfo(LogComponent.TRANSACTION_HISTORY_REPOSITORY, "setup completed")
    }

    suspend fun writeHistory(
        session: ClientSession,
        transactions: List<Transaction>,
        resolvedInputUtxos: Map<OutPoint, Utxo>,
        height: Int,
        blockTimestamp: Long
    ): Boolean {
        return transactions.isEmpty() || try {
            val documents = transactions.map { tx ->
                val inputUtxos = tx.inputs.mapNotNull { input ->
                    resolvedInputUtxos[
                        OutPoint(
                            txHash = input.prevTxHash,
                            outputIndex = input.outputIndex
                        )
                    ]
                }

                val inputAmount = inputUtxos.fold(0L) { sum, utxo ->
                    Math.addExact(sum, utxo.amount)
                }

                val outputAmount = tx.outputs.fold(0L) { sum, output ->
                    Math.addExact(sum, output.amount)
                }

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
                    .append("height", height)
                    .append("blockTimestamp", blockTimestamp)
                    .append("txTimestamp", tx.timestamp)
                    .append("memo", tx.memo)
                    .append("relatedPubKeys", relatedPubKeys)
            }

            if (documents.isEmpty()) return true
            val result = collection.insertMany(session, documents)
            result.wasAcknowledged()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.TRANSACTION_HISTORY_REPOSITORY,
                "failed to write transaction history",
                e,
                "height" to height
            )
            false
        }
    }

    suspend fun getHistory(pubKey: String): List<TransactionHistoryEntry> {
        return try {
            collection.find(Filters.eq("relatedPubKeys", pubKey))
                .sort(Sorts.descending("blockTimestamp", "txTimestamp"))
                .map { it.toTransactionHistoryEntry() }
                .toList()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.TRANSACTION_HISTORY_REPOSITORY,
                "failed to get transaction history",
                e,
                "pubKey" to pubKey
            )
            emptyList()
        }
    }

    private fun Document.toTransactionHistoryEntry(): TransactionHistoryEntry {
        val transactionDocument = get("transaction", Document::class.java)

        return TransactionHistoryEntry(
            txHash = getString("txHash"),
            transaction = transactionDocument.toTransaction(),
            inputUtxos = getList("inputUtxos", Document::class.java)
                .orEmpty()
                .map { it.toUtxo() },
            fee = get("fee", Number::class.java)?.toLong() ?: 0L,
            height = getInteger("height") ?: 0,
            blockTimestamp = get("blockTimestamp", Number::class.java)?.toLong() ?: 0L,
            txTimestamp = get("txTimestamp", Number::class.java)?.toLong() ?: 0L,
            memo = getString("memo") ?: "",
            relatedPubKeys = getList("relatedPubKeys", String::class.java).orEmpty()
        )
    }
}