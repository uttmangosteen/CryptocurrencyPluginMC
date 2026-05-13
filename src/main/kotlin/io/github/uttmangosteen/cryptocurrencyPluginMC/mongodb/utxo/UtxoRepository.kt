package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.utxo

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.bson.Document
import java.util.logging.Logger

class UtxoRepository(
    database: MongoDatabase,
    private val logger: Logger
) {
    private val collection = database.getCollection<Document>("utxos")
}