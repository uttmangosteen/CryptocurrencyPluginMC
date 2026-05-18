package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.miningmachine

import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.CreateBlockMode
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.MiningMachine
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.MiningMachineStatus
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.Gpu
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toBlock
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.toDocument
import org.bson.Document

fun MiningMachine.toDocument(): Document {
    normalizeGpuSlots()

    return Document("_id", id)
        .append("userUuids", userUuids)
        .append("enabled", enabled)
        .append("status", status.name)
        .append("createBlockMode", createBlockMode.name)
        .append("rewardAccountPubKey", rewardAccountPubKey)
        .append("defaultMemo", defaultMemo)
        .append("memo", memo)
        .append("miningBlock", miningBlock?.toDocument())
        .append("fuelAmount", fuelAmount)
        .append("gpuSlots", gpuSlots.map { gpu -> gpu?.toDocument() })
}

fun Document.toMiningMachine(): MiningMachine {
    val gpuDocuments = getList("gpuSlots", Document::class.java).orEmpty()
    val gpuSlots = gpuDocuments
        .map { document -> document?.toGpu() }
        .toMutableList()

    val miningBlockDocument = get("miningBlock", Document::class.java)

    val machine = MiningMachine(
        id = getString("_id"),
        userUuids = getList("userUuids", String::class.java).orEmpty().toMutableList(),
        enabled = getBoolean("enabled") ?: false,
        status = getString("status")
            ?.let { runCatching { MiningMachineStatus.valueOf(it) }.getOrNull() }
            ?: MiningMachineStatus.IDLE,
        createBlockMode = getString("createBlockMode")
            ?.let { runCatching { CreateBlockMode.valueOf(it) }.getOrNull() }
            ?: CreateBlockMode.NONE,
        rewardAccountPubKey = getString("rewardAccountPubKey"),
        defaultMemo = getString("defaultMemo") ?: "",
        memo = getString("memo") ?: "",
        miningBlock = miningBlockDocument?.toBlock(),
        fuelAmount = get("fuelAmount", Number::class.java)?.toInt() ?: 0,
        gpuSlots = gpuSlots
    )

    machine.normalizeGpuSlots()
    machine.refreshStatus()
    return machine
}

fun Gpu.toDocument(): Document {
    return Document()
        .append("gpuName", gpuName)
        .append("material", material)
        .append("customModelData", customModelData.toDouble())
        .append("description", description)
        .append("life", life)
        .append("breakChance", breakChance)
        .append("power", power)
}

fun Document.toGpu(): Gpu {
    return Gpu(
        gpuName = getString("gpuName") ?: "§c§l名称未設定",
        material = getString("material") ?: "IRON_INGOT",
        customModelData = get("customModelData", Number::class.java)?.toFloat() ?: 0f,
        description = getString("description") ?: "§c§l説明未設定",
        life = get("life", Number::class.java)?.toInt() ?: 0,
        breakChance = get("breakChance", Number::class.java)?.toDouble() ?: 0.0,
        power = get("power", Number::class.java)?.toInt() ?: 0
    )
}

