package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.miningmachine

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.MiningMachine
import java.util.logging.Logger

class MiningMachineRepository(
    database: MongoDatabase,
    private val logger: Logger
) {
    private val collection = database.getCollection<MiningMachine>("mining_machines")

    suspend fun setup() {
        collection.createIndex(
            Indexes.ascending("id"),
            IndexOptions()
                .unique(true)
                .name("mining_machine_id_unique")
        )

        collection.createIndex(
            Indexes.ascending("ownerUuid"),
            IndexOptions().name("mining_machine_owner")
        )

        logger.ccInfo(LogComponent.MINING_MACHINE_REPOSITORY, "setup completed")
    }

}