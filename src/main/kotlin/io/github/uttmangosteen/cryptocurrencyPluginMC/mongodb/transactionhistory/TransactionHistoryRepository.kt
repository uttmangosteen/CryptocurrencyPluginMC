package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.transactionhistory

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.bson.Document
import java.util.logging.Logger

class TransactionHistoryRepository(
    database: MongoDatabase,
    private val logger: Logger
) {
    private val collection = database.getCollection<Document>("transaction_history")
}