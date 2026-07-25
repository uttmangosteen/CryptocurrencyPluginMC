package io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.Gpu
import org.bukkit.Bukkit
import java.util.UUID

data class MiningMachine(
    val id: String = UUID.randomUUID().toString(),
    val userUuids: MutableList<String>, // index:0のuuidがownerUuid

    var enabled: Boolean = false,
    var status: MiningMachineStatus = MiningMachineStatus.IDLE,

    var createBlockMode: CreateBlockMode = CreateBlockMode.NONE,
    var rewardAccountPubKey: String? = null,

    var memo: String = "",
    var shareNameOnMined: Boolean = false,

    var miningBlock: Block? = null,

    var fuelAmount: Int = 0,

    var gpuSlots: MutableList<Gpu?> = MutableList(MAX_GPU_SLOTS) { null },
) {
    //DBと状態がズレているか?
    @Transient
    var isDirty: Boolean = false

    companion object {
        const val MAX_GPU_SLOTS = 8
        const val MAX_USERS = 4
        const val MAX_FUEL_AMOUNT = 999999

        fun create(machineId: String, ownerUuid: String): MiningMachine {
            return MiningMachine(
                id = machineId,
                userUuids = mutableListOf(ownerUuid),
                memo = "MinedBy" + Bukkit.getOfflinePlayer(UUID.fromString(ownerUuid)).name
            )
        }
    }

    val ownerUuid: String? get() = userUuids.firstOrNull()

    fun isOwner(uuid: String): Boolean {
        return ownerUuid == uuid
    }

    fun canAccess(uuid: String): Boolean {
        return uuid in userUuids
    }

    fun activeGpuCount(): Int {
        var count = 0
        for (gpu in gpuSlots) {
            if (gpu != null && gpu.isActive()) count++
        }
        return count
    }

    fun totalGpuPower(): Int {
        var power = 0
        for (gpu in gpuSlots) {
            if (gpu != null && gpu.isActive()) {
                power += gpu.power.coerceAtLeast(0)
            }
        }
        return power
    }

    fun hasActiveGpu(): Boolean {
        for (gpu in gpuSlots) {
            if (gpu != null && gpu.isActive() && gpu.power > 0) return true
        }
        return false
    }

    fun addUser(uuid: String): Boolean {
        if (uuid.isBlank()) return false
        if (userUuids.size >= MAX_USERS) return false
        if (uuid in userUuids) return false
        userUuids.add(uuid)
        isDirty = true
        return true
    }

    fun removeUser(uuid: String): Boolean {
        if (uuid.isBlank()) return false
        if (isOwner(uuid)) return false
        val removed = userUuids.remove(uuid)
        if (removed) isDirty = true
        return removed
    }

    //DBとの兼ね合いでnull埋め手動
    fun normalizeGpuSlots() {
        var changed = false
        if (gpuSlots.size < MAX_GPU_SLOTS) {
            repeat(MAX_GPU_SLOTS - gpuSlots.size) {
                gpuSlots.add(null)
            }
            changed = true
        }
        if (gpuSlots.size > MAX_GPU_SLOTS) {
            gpuSlots = gpuSlots.take(MAX_GPU_SLOTS).toMutableList()
            changed = true
        }
        if (changed) isDirty = true
    }

    fun setGpu(slot: Int, gpu: Gpu): Boolean {
        normalizeGpuSlots()
        if (slot !in 0 until MAX_GPU_SLOTS) return false
        if (gpuSlots[slot] != null) return false
        if (!gpu.isActive()) return false

        gpuSlots[slot] = gpu
        refreshStatus()
        isDirty = true
        return true
    }

    fun takeGpu(slot: Int): Gpu? {
        normalizeGpuSlots()
        if (slot !in 0 until MAX_GPU_SLOTS) return null

        val gpu = gpuSlots[slot] ?: return null
        gpuSlots[slot] = null
        refreshStatus()
        isDirty = true
        return gpu
    }

    fun addFuel(amount: Int): Boolean {
        if (amount <= 0) return false
        val oldFuelAmount = fuelAmount
        val added = fuelAmount + amount
        fuelAmount = added.coerceAtMost(MAX_FUEL_AMOUNT)
        if (fuelAmount == oldFuelAmount) return false
        refreshStatus()
        isDirty = true
        return true
    }

    fun consumeFuel(amount: Int): Boolean {
        if (amount <= 0) return false
        if (fuelAmount < amount) return false
        fuelAmount -= amount
        refreshStatus()
        isDirty = true
        return true
    }

    fun toggleEnabled(): Boolean {
        if (enabled) {
            enabled = false
            status = MiningMachineStatus.DISABLED
            isDirty = true
            return true
        }

        if (rewardAccountPubKey == null) return false
        if (!hasActiveGpu()) return false
        if (fuelAmount <= 0) return false
        enabled = true
        status = MiningMachineStatus.MINING
        isDirty = true
        return true
    }

    fun refreshStatus() {
        val oldStatus = status
        val activeGpuCount = activeGpuCount()
        status = when {
            !enabled -> MiningMachineStatus.DISABLED
            rewardAccountPubKey == null -> MiningMachineStatus.IDLE
            activeGpuCount == 0 -> MiningMachineStatus.IDLE
            fuelAmount < activeGpuCount -> MiningMachineStatus.IDLE
            else -> MiningMachineStatus.MINING
        }
        if (oldStatus != status) isDirty = true
    }

    fun halt() {
        enabled = false
        status = MiningMachineStatus.DISABLED
        miningBlock = null
        isDirty = true
    }

    fun setRewardAccountPubKey(publicKey: String): Boolean {
        rewardAccountPubKey = publicKey
        refreshStatus()
        isDirty = true
        return true
    }

    fun setMiningMemo(value: String): Boolean {
        memo = value
        miningBlock = null
        isDirty = true
        return true
    }

    fun toggleCreateBlockMode(): CreateBlockMode {
        createBlockMode = when (createBlockMode) {
            CreateBlockMode.NONE -> CreateBlockMode.ONLY_MINE
            CreateBlockMode.ONLY_MINE -> CreateBlockMode.FEE_SORT
            CreateBlockMode.FEE_SORT -> CreateBlockMode.MINE_AND_FEE_SORT
            CreateBlockMode.MINE_AND_FEE_SORT -> CreateBlockMode.NONE
        }
        isDirty = true
        return createBlockMode
    }

    fun toggleShareNameOnMined(): Boolean {
        shareNameOnMined = !shareNameOnMined
        isDirty = true
        return shareNameOnMined
    }

    fun replaceMiningBlock(block: Block?) {
        if (miningBlock === block) return
        miningBlock = block
        refreshStatus()
        isDirty = true
    }

    fun clearMiningBlock(): Boolean {
        if (miningBlock == null) return false
        miningBlock = null
        isDirty = true
        return true
    }

    fun consumeGpuLife() {
        var changed = false
        for (gpu in gpuSlots) {
            if (gpu == null) continue
            gpu.consumeLife()
            changed = true
        }
        if (!changed) return
        refreshStatus()
        isDirty = true
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