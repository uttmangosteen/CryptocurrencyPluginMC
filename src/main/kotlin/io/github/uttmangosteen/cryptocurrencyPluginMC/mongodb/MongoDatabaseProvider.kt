package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.bson.codecs.configuration.CodecRegistries.fromProviders
import org.bson.codecs.configuration.CodecRegistries.fromRegistries
import org.bson.codecs.pojo.PojoCodecProvider

class MongoDatabaseProvider(
    connectionString: String,
    database: String
) {
    private val codecRegistry = fromRegistries(
        MongoClientSettings.getDefaultCodecRegistry(),
        fromProviders(
            PojoCodecProvider.builder()
                .automatic(true)
                .build()
        )
    )

    private val settings = MongoClientSettings.builder()
        .applyConnectionString(ConnectionString(connectionString))
        .codecRegistry(codecRegistry)
        .build()

    val client: MongoClient = MongoClient.create(settings)
    val database: MongoDatabase = client.getDatabase(database)

    suspend fun startSession(): ClientSession {
        return client.startSession()
    }

    fun close() {
        client.close()
    }
}