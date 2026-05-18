package io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.Gpu
import java.util.UUID

data class MiningMachine(
    val id: String = UUID.randomUUID().toString(),
    val userUuids: MutableList<String>, // index:0のuuidがownerUuid

    var enabled: Boolean = false,
    var status: MiningMachineStatus = MiningMachineStatus.IDLE,

    var createBlockMode: CreateBlockMode = CreateBlockMode.NONE,
    var rewardAccountPubKey: String? = null,

    var defaultMemo: String = "",
    var memo: String = "",

    var shareNameOnMined: Boolean = false,

    var miningBlock: Block? = null,

    var fuelAmount: Int = 0,

    var gpuSlots: MutableList<Gpu?> = MutableList(MAX_GPU_SLOTS) { null },
) {
    companion object {
        const val MAX_GPU_SLOTS = 8
        const val MAX_USERS = 16
        const val MAX_FUEL_AMOUNT = 1_000_000

        fun create(ownerUuid: String): MiningMachine {
            return MiningMachine(
                userUuids = mutableListOf(ownerUuid),
                defaultMemo = "",
                memo = ""
            )
        }
        private const val MEMO_MAX_LENGTH = 32
    }

    val ownerUuid: String?
        get() = userUuids.firstOrNull()

    fun isOwner(uuid: String): Boolean {
        return ownerUuid == uuid
    }

    fun canAccess(uuid: String): Boolean {
        return uuid in userUuids
    }

    fun addUser(uuid: String): Boolean {
        if (uuid.isBlank()) return false
        if (userUuids.size >= MAX_USERS) return false
        if (uuid in userUuids) return false
        userUuids.add(uuid)
        return true
    }

    fun removeUser(uuid: String): Boolean {
        if (uuid.isBlank()) return false
        if (isOwner(uuid)) return false
        return userUuids.remove(uuid)
    }

    fun normalizeGpuSlots() {
        if (gpuSlots.size < MAX_GPU_SLOTS) {
            repeat(MAX_GPU_SLOTS - gpuSlots.size) {
                gpuSlots.add(null)
            }
        }
        if (gpuSlots.size > MAX_GPU_SLOTS) {
            gpuSlots = gpuSlots.take(MAX_GPU_SLOTS).toMutableList()
        }
    }

    fun setGpu(slot: Int, gpu: Gpu): Boolean {
        normalizeGpuSlots()
        if (slot !in 0 until MAX_GPU_SLOTS) return false
        if (gpuSlots[slot] != null) return false
        if (!gpu.isActive()) return false

        gpuSlots[slot] = gpu
        refreshStatus()
        return true
    }

    fun takeGpu(slot: Int): Gpu? {
        normalizeGpuSlots()
        if (slot !in 0 until MAX_GPU_SLOTS) return null

        val gpu = gpuSlots[slot] ?: return null
        gpuSlots[slot] = null
        refreshStatus()
        return gpu
    }

    fun totalGpuPower(): Int {
        normalizeGpuSlots()
        return gpuSlots
            .filterNotNull()
            .filter { it.isActive() }
            .sumOf { it.power.coerceAtLeast(0) }
    }

    fun hasActiveGpu(): Boolean {
        return totalGpuPower() > 0
    }

    fun addFuel(amount: Int): Boolean {
        if (amount <= 0) return false
        val added = fuelAmount.toLong() + amount.toLong()
        fuelAmount = added.coerceAtMost(MAX_FUEL_AMOUNT.toLong()).toInt()
        refreshStatus()
        return true
    }

    fun consumeFuel(amount: Int): Boolean {
        if (amount <= 0) return false
        if (fuelAmount < amount) return false
        fuelAmount -= amount
        refreshStatus()
        return true
    }

    fun run(): Boolean {
        if (rewardAccountPubKey == null) return false
        if (!hasActiveGpu()) return false
        if (fuelAmount <= 0) return false

        enabled = true
        status = MiningMachineStatus.MINING
        return true
    }

    fun halt(): Boolean {
        enabled = false
        status = MiningMachineStatus.DISABLED
        return true
    }

    fun refreshStatus() {
        status = when {
            !enabled -> MiningMachineStatus.DISABLED
            rewardAccountPubKey == null -> MiningMachineStatus.IDLE
            fuelAmount <= 0 -> MiningMachineStatus.IDLE
            !hasActiveGpu() -> MiningMachineStatus.IDLE
            else -> MiningMachineStatus.MINING
        }
    }

    fun setRewardAccountPubKey(publicKey: String?): Boolean {
        rewardAccountPubKey = publicKey
        refreshStatus()
        return true
    }

    fun setDefaultMemo(value: String): Boolean {
        defaultMemo = value.take(MEMO_MAX_LENGTH)
        if (memo.isBlank()) memo = defaultMemo
        return true
    }

    fun setMiningMemo(value: String): Boolean {
        memo = value.take(MEMO_MAX_LENGTH)
        return true
    }

    fun toggleCreateBlockMode(): CreateBlockMode {
        createBlockMode = when (createBlockMode) {
            CreateBlockMode.NONE -> CreateBlockMode.ONLY_MINE
            CreateBlockMode.ONLY_MINE -> CreateBlockMode.FEE_SORT
            CreateBlockMode.FEE_SORT -> CreateBlockMode.MINE_AND_FEE_SORT
            CreateBlockMode.MINE_AND_FEE_SORT -> CreateBlockMode.NONE
        }
        return createBlockMode
    }

    fun toggleShareNameOnMined(): Boolean {
        shareNameOnMined = !shareNameOnMined
        return shareNameOnMined
    }

    fun setMiningBlock(block: Block?) {
        miningBlock = block
        refreshStatus()
    }

    fun consumeGpuLife() {
        normalizeGpuSlots()
        gpuSlots.filterNotNull().forEach { it.consumeLife() }
        refreshStatus()
    }
}

enum class MiningMachineStatus {
    IDLE,
    MINING,
    DISABLED
}

enum class CreateBlockMode {
    NONE,
    ONLY_MINE,
    FEE_SORT,
    MINE_AND_FEE_SORT
}