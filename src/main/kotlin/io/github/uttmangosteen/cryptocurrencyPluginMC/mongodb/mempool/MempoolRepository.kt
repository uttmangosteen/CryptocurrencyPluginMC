package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.mempool

import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.OutPoint
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.TxInput
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.TxOutput
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.CreateBlockMode
import io.github.uttmangosteen.cryptocurrencyPluginMC.util.decodeHex
import io.github.uttmangosteen.cryptocurrencyPluginMC.util.toHex
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
                Indexes.ascending("outpoints.txHashHex"),
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
                "txHash" to entity.txHashHex
            )
            false
        }
    }

    suspend fun delete(block: Block): Boolean {
        val transactions = block.transactions
        if (transactions.isEmpty()) return true

        val hashes = transactions.map { it.txHash.toHex() }

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
        minerPubKeyHex: String,
        limit: Int
    ): List<TransactionEntry> {
        return when (mode) {
            CreateBlockMode.NONE -> emptyList()

            CreateBlockMode.ONLY_MINE -> {
                collection.find(Filters.eq("pubkeyList", minerPubKeyHex))
                    .sort(Sorts.descending("fee"))
                    .limit(limit)
                    .map { it.toTransactionEntity() }
                    .toList()
            }

            CreateBlockMode.FEE_SORT -> {
                // 手数料が高い順に取得
                collection.find()
                    .sort(Sorts.descending("fee"))
                    .limit(limit)
                    .map { it.toTransactionEntity() }
                    .toList()
            }

            CreateBlockMode.MINE_AND_FEE_SORT -> {
                val mine = collection.find(Filters.eq("pubkeyList", minerPubKeyHex))
                    .sort(Sorts.descending("fee"))
                    .limit(limit)
                    .map { it.toTransactionEntity() }
                    .toList()

                val remaining = limit - mine.size
                if (remaining <= 0) return mine

                val mineHashes = mine.map { it.txHashHex }

                val others = collection.find(Filters.nin("_id", mineHashes))
                    .sort(Sorts.descending("fee"))
                    .limit(remaining)
                    .map { it.toTransactionEntity() }
                    .toList()

                mine + others
            }
        }
    }

    private fun TransactionEntry.toDocument(): Document {
        return Document("_id", txHashHex)
            .append("transaction", transaction.toDocument())
            .append("fee", fee)
            .append("txHashHex", txHashHex)
            .append("timestamp", timestamp)
            .append("pubkeyList", pubkeyList)
            .append("outpoints", outpoints.map { outPoint -> outPoint.toDocument() })
    }

    private fun Document.toTransactionEntity(): TransactionEntry {
        val transactionDocument = get("transaction", Document::class.java)

        return TransactionEntry(
            transaction = transactionDocument.toTransaction(),
            fee = getLong("fee") ?: 0L
        )
    }

    private fun Transaction.toDocument(): Document {
        return Document("isCoinbase", isCoinbase)
            .append("inputs", inputs.map { input -> input.toDocument() })
            .append("outputs", outputs.map { output -> output.toDocument() })
            .append("timestamp", timestamp)
            .append("memo", memo)
            .append("txHashHex", txHash.toHex())
    }

    private fun Document.toTransaction(): Transaction {
        val inputs = getList("inputs", Document::class.java)
            .orEmpty()
            .map { inputDocument -> inputDocument.toTxInput() }

        val outputs = getList("outputs", Document::class.java)
            .orEmpty()
            .map { outputDocument -> outputDocument.toTxOutput() }

        return Transaction(
            isCoinbase = getBoolean("isCoinbase") ?: false,
            inputs = inputs,
            outputs = outputs,
            timestamp = getLong("timestamp") ?: 0L,
            memo = getString("memo") ?: "",
            txHash = getString("txHashHex").decodeHex()
        )
    }

    private fun TxInput.toDocument(): Document {
        return Document("prevTxHashHex", prevTxHash.toHex())
            .append("outputIndex", outputIndex)
            .append("signatureHex", signature?.toHex())
            .append("publicKeyHex", publicKey.toHex())
    }

    private fun Document.toTxInput(): TxInput {
        return TxInput(
            prevTxHash = getString("prevTxHashHex").decodeHex(),
            outputIndex = getInteger("outputIndex") ?: 0,
            signature = getString("signatureHex")?.decodeHex(),
            publicKey = getString("publicKeyHex").decodeHex()
        )
    }

    private fun TxOutput.toDocument(): Document {
        return Document("amount", amount)
            .append("receiverPubKeyHex", receiverPubKey.toHex())
    }

    private fun Document.toTxOutput(): TxOutput {
        return TxOutput(
            amount = getLong("amount") ?: 0L,
            receiverPubKey = getString("receiverPubKeyHex").decodeHex()
        )
    }

    private fun OutPoint.toDocument(): Document {
        return Document("txHashHex", txHashHex)
            .append("outputIndex", outputIndex)
    }
}