package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.miningmachine

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.bson.Document
import java.util.logging.Logger

class MiningMachineRepository(
    database: MongoDatabase,
    private val logger: Logger
) {
    private val collection = database.getCollection<Document>("mining_machines")
}