package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.mempool

import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.CreateBlockMode
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toDocument
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toOutPoint
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toTransaction
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.util.logging.Logger

class MempoolRepository(
    database: MongoDatabase,
    private val logger: Logger
) {
    private val collection = database.getCollection<Document>("mempool")

    suspend fun setup() {
        // CreateBlockMode.FEE_SORT用
        collection.createIndex(
            Indexes.descending("fee")
        )

        // CreateBlockMode.ONLY_MINE & MINE_AND_FEE_SORT用
        collection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("pubkeyList"),
                Indexes.descending("fee")
            )
        )

        // 2重支払い防止用
        // 同じ OutPoint を消費するトランザクションは mempool に1つしか入れない
        collection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("outpoints.txHash"),
                Indexes.ascending("outpoints.outputIndex")
            ),
            IndexOptions()
                .unique(true)
                .name("unique_outpoint")
        )

        logger.ccInfo(LogComponent.MEMPOOL_REPOSITORY, "setup completed")
    }

    suspend fun save(entity: TransactionEntry): Boolean {
        return try {
            val result = collection.insertOne(entity.toDocument())
            result.wasAcknowledged()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.MEMPOOL_REPOSITORY,
                "failed to save mempool tx",
                e,
                "txHash" to entity.txHash
            )
            false
        }
    }

    suspend fun delete(block: Block): Boolean {
        val transactions = block.transactions
        if (transactions.isEmpty()) return true

        val hashes = transactions.map { it.txHash }

        return try {
            val result = collection.deleteMany(Filters.`in`("_id", hashes))
            result.wasAcknowledged()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.MEMPOOL_REPOSITORY,
                "failed to delete mempool tx in block",
                e,
                "height" to block.height
            )
            false
        }
    }

    suspend fun getTxForMining(
        mode: CreateBlockMode,
        minerPubKey: String,
        limit: Int
    ): List<TransactionEntry> {
        return try {
            when (mode) {
                CreateBlockMode.NONE -> emptyList()

                CreateBlockMode.ONLY_MINE -> {
                    collection.find(Filters.eq("pubkeyList", minerPubKey))
                        .sort(Sorts.descending("fee"))
                        .limit(limit)
                        .map { it.toTransactionEntity() }
                        .toList()
                }

                CreateBlockMode.FEE_SORT -> {
                    collection.find()
                        .sort(Sorts.descending("fee"))
                        .limit(limit)
                        .map { it.toTransactionEntity() }
                        .toList()
                }

                CreateBlockMode.MINE_AND_FEE_SORT -> {
                    val mine = collection.find(Filters.eq("pubkeyList", minerPubKey))
                        .sort(Sorts.descending("fee"))
                        .limit(limit)
                        .map { it.toTransactionEntity() }
                        .toList()

                    val remaining = limit - mine.size
                    if (remaining <= 0) {
                        mine
                    } else {
                        val mineHashes = mine.map { it.txHash }

                        val others = collection.find(Filters.nin("_id", mineHashes))
                            .sort(Sorts.descending("fee"))
                            .limit(remaining)
                            .map { it.toTransactionEntity() }
                            .toList()

                        mine + others
                    }
                }
            }
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.MEMPOOL_REPOSITORY,
                "failed to get mempool txs for mining",
                e,
                "mode" to mode,
                "minerPubKey" to minerPubKey,
                "limit" to limit
            )
            emptyList()
        }
    }

    private fun TransactionEntry.toDocument(): Document {
        return Document("_id", txHash)

            .append("transaction", transaction.toDocument())

            .append("fee", fee)

            .append("timestamp", timestamp)
            .append("pubkeyList", pubkeyList)
            .append("outpoints", outpoints.map { outPoint -> outPoint.toDocument() })
    }

    private fun Document.toTransactionEntity(): TransactionEntry {
        val transactionDocument = get("transaction", Document::class.java)

        return TransactionEntry(
            transaction = transactionDocument.toTransaction(),
            txHash = getString("_id"),
            fee = get("fee", Number::class.java).toLong(),
            timestamp = get("timestamp", Number::class.java).toLong(),
            pubkeyList = getList("pubkeyList", String::class.java).orEmpty(),
            outpoints = getList("outpoints", Document::class.java).orEmpty().map { it.toOutPoint() }
        )
    }
}