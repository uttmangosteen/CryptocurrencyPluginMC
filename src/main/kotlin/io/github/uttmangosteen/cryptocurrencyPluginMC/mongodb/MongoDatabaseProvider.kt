package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb

import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase

class MongoDatabaseProvider(
    connectionString: String,
    database: String
) {
    val client: MongoClient = MongoClient.create(connectionString)
    val database: MongoDatabase = client.getDatabase(database)

    suspend fun startSession(): ClientSession {
        return client.startSession()
    }

    fun close() {
        client.close()
    }
}