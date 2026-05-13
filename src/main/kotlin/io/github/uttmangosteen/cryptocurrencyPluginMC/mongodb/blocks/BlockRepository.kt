package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blocks

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent

import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import org.bson.Document
import java.util.logging.Logger

class BlockRepository(
    database: MongoDatabase,
    private val logger: Logger
) {
    private val collection = database.getCollection<Document>("blocks")

    suspend fun setup() {
        collection.createIndex(
            Indexes.ascending("height"),
            IndexOptions()
                .unique(true)
                .name("unique_block_height")
        )

        logger.ccInfo(LogComponent.BLOCK_REPOSITORY, "setup completed")
    }

}