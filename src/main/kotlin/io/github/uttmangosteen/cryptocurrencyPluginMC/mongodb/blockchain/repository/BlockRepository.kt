package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.repository

import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toBlock
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toDocument
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import java.util.logging.Logger

class BlockRepository(
    database: MongoDatabase,
    private val logger: Logger
) {
    private val collection = database.getCollection<Document>("blocks")

    suspend fun setup() {
        if (getLatestBlock() == null) {
            createGenesisBlock()
        }
        logger.ccInfo(LogComponent.BLOCK_REPOSITORY, "setup completed")
    }

    private suspend fun createGenesisBlock() {
        try {
            val genesisBlock = Block.createGenesis()
            val result = collection.insertOne(genesisBlock.toDocument())

            if (result.wasAcknowledged()) {
                logger.ccInfo(
                    LogComponent.BLOCK_REPOSITORY,
                    "genesis block created",
                    "hash" to genesisBlock.hash
                )
            }
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.BLOCK_REPOSITORY,
                "failed to create genesis block",
                e
            )
        }
    }

    //acceptNewBlock時実行
    suspend fun saveBlock(session: ClientSession, block: Block): Boolean {
        return try {
            val result = collection.insertOne(session, block.toDocument())
            result.wasAcknowledged()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.BLOCK_REPOSITORY,
                "failed to save block",
                e,
                "height" to block.height
            )
            false
        }
    }

    suspend fun getLatestBlock(session: ClientSession? = null): Block? {
        return try {
            val findPublisher = if (session != null) collection.find(session) else collection.find()
            findPublisher
                .sort(Sorts.descending("_id"))
                .firstOrNull()
                ?.toBlock()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.BLOCK_REPOSITORY,
                "failed to get latest block in session",
                e
            )
            null
        }
    }
}