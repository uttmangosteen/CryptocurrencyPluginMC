package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.miningmachine

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.MiningMachine
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.MiningMachineStatus
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.util.logging.Logger

class MiningMachineRepository(
    database: MongoDatabase,
    private val logger: Logger
) {
    private val collection = database.getCollection<Document>("mining_machines")

    suspend fun setup() {
        collection.createIndex(Indexes.ascending("userUuids"))
        collection.createIndex(Indexes.ascending("enabled"))
        collection.createIndex(Indexes.ascending("status"))
        collection.createIndex(Indexes.ascending("rewardAccountPubKey"))

        logger.ccInfo(LogComponent.MINING_MACHINE_REPOSITORY, "setup completed")
    }

    suspend fun create(ownerUuid: String): MiningMachine? {
        if (ownerUuid.isBlank()) return null

        val machine = MiningMachine.create(ownerUuid)
        val saved = save(machine)
        if (!saved) return null

        return machine
    }

    suspend fun save(machine: MiningMachine): Boolean {
        return try {
            machine.normalizeGpuSlots()
            machine.refreshStatus()

            val result = collection.replaceOne(
                Filters.eq("_id", machine.id),
                machine.toDocument(),
                ReplaceOptions().upsert(true)
            )

            result.wasAcknowledged()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.MINING_MACHINE_REPOSITORY,
                "failed to save mining machine",
                e,
                "machineId" to machine.id
            )
            false
        }
    }

    suspend fun get(machineId: String): MiningMachine? {
        if (machineId.isBlank()) return null

        return try {
            collection.find(Filters.eq("_id", machineId))
                .firstOrNull()
                ?.toMiningMachine()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.MINING_MACHINE_REPOSITORY,
                "failed to get mining machine",
                e,
                "machineId" to machineId
            )
            null
        }
    }

    suspend fun delete(machineId: String, requesterUuid: String): Boolean {
        if (machineId.isBlank()) return false
        if (requesterUuid.isBlank()) return false

        return try {
            val machine = get(machineId) ?: return false
            if (!machine.isOwner(requesterUuid)) return false

            val result = collection.deleteOne(Filters.eq("_id", machineId))
            result.wasAcknowledged() && result.deletedCount == 1L
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.MINING_MACHINE_REPOSITORY,
                "failed to delete mining machine",
                e,
                "machineId" to machineId,
                "requesterUuid" to requesterUuid
            )
            false
        }
    }

    suspend fun updateMachine(
        machineId: String,
        requesterUuid: String,
        requireOwner: Boolean = false,
        block: (MiningMachine) -> Boolean
    ): Boolean {
        if (machineId.isBlank()) return false
        if (requesterUuid.isBlank()) return false

        val machine = get(machineId) ?: return false

        val allowed = if (requireOwner) {
            machine.isOwner(requesterUuid)
        } else {
            machine.canAccess(requesterUuid)
        }

        if (!allowed) return false

        val changed = block(machine)
        if (!changed) return false

        return save(machine)
    }

    suspend fun getRunnableMachines(): List<MiningMachine> {
        return try {
            collection.find(
                Filters.and(
                    Filters.eq("enabled", true),
                    Filters.eq("status", MiningMachineStatus.MINING.name),
                    Filters.ne("rewardAccountPubKey", null),
                    Filters.gt("fuelAmount", 0)
                )
            )
                .map { it.toMiningMachine() }
                .toList()
                .filter { machine ->
                    machine.hasActiveGpu() && machine.rewardAccountPubKey != null
                }
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.MINING_MACHINE_REPOSITORY,
                "failed to get runnable mining machines",
                e
            )
            emptyList()
        }
    }

    suspend fun calculateNetworkMiningPower(): Long {
        return try {
            getRunnableMachines().fold(0L) { sum, machine ->
                Math.addExact(sum, machine.totalGpuPower().toLong())
            }
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.MINING_MACHINE_REPOSITORY,
                "failed to calculate network mining power",
                e
            )
            0L
        }
    }
}