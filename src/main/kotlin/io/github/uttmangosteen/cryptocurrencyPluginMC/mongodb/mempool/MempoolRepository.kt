package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.mempool

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.CreateBlockMode
import io.github.uttmangosteen.cryptocurrencyPluginMC.util.toHex
import kotlinx.coroutines.flow.toList
import java.util.logging.Logger

class MempoolRepository(
    database: MongoDatabase,
    private val logger: Logger
) {
    private val collection = database.getCollection<TransactionEntity>("mempool")

    suspend fun setup() {
        //CreateBlockMode.FEE_SORT用
        collection.createIndex(
            Indexes.descending("fee")
        )

        //CreateBlockMode.ONLY_MINE & MINE_AND_FEE_SORT用
        collection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("pubkeyList"),
                Indexes.descending("fee")
            )
        )

        //2重支払いチェック用
        collection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("consumedOutpoints.txHashHex"),
                Indexes.ascending("consumedOutpoints.outputIndex")
            )
        )

        logger.ccInfo(LogComponent.MEMPOOL_REPOSITORY, "setup completed")
    }

    suspend fun save(entity: TransactionEntity): Boolean {
        return try {
            val result = collection.insertOne(entity)
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
    ): List<TransactionEntity> {
        return when (mode) {
            CreateBlockMode.NONE -> emptyList()
            CreateBlockMode.ONLY_MINE ->{
                collection.find(Filters.eq("pubkeyList", minerPubKeyHex))
                    .sort(Sorts.descending("fee"))
                    .limit(limit)
                    .toList()
            }
            CreateBlockMode.FEE_SORT -> {
                // 手数料が高い順に取得
                collection.find()
                    .sort(Sorts.descending("fee"))
                    .limit(limit)
                    .toList()
            }
            CreateBlockMode.MINE_AND_FEE_SORT -> {
                val mine = collection.find(Filters.eq("pubkeyList", minerPubKeyHex))
                    .sort(Sorts.descending("fee"))
                    .limit(limit)
                    .toList()
                val remaining = limit - mine.size
                if (remaining <= 0) return mine
                val mineHashes = mine.map { it.txHashHex }
                val others = collection.find(Filters.nin("_id", mineHashes)) // _id が mineHashes に含まれないもの
                    .sort(Sorts.descending("fee"))
                    .limit(remaining)
                    .toList()
                mine + others
            }
        }
    }
}