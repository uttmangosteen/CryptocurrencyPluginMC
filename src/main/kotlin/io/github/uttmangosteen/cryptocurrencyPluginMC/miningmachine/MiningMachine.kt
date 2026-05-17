package io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.Gpu
import org.bson.codecs.pojo.annotations.BsonId
import java.util.UUID

data class MiningMachine(
    val id: String = UUID.randomUUID().toString(),
    val ownerUuid: String,

    var enabled: Boolean = false,
    var status: MiningMachineStatus = MiningMachineStatus.IDLE,

    var createBlockMode: CreateBlockMode = CreateBlockMode.NONE,
    var rewardAccountId: String? = null,
    var defaultMemo: String = "",

    var candidateBlock: Block? = null,
    var currentNonce: Long = 0L,

    var fuelAmount: Int = 0,

    var gpuSlots: MutableList<Gpu?> = MutableList(MAX_GPU_SLOTS) { null },
) {
    companion object {
        const val MAX_GPU_SLOTS = 8//最大GPU搭載数(帰る予定はない)
    }

    // gpuSlots の長さを MAX_GPU_SLOTS に正規化する
    // DB からの復元時に長さが変わっている場合に備えて呼び出す
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

    // アクティブな全 GPU の合計パワーを返す
    // 1度に試行する nonce の数に等しい
    fun totalGpuPower(): Int {
        normalizeGpuSlots()
        return gpuSlots
            .filterNotNull()
            .filter { it.isActive() }
            .sumOf { it.power.coerceAtLeast(0) }
    }
}

enum class MiningMachineStatus {
    IDLE,// 待機中(GPUなしor燃料なしorブロック確定待ち)
    MINING,// ハッシュ計算中
    DISABLED// 電源off
}

enum class CreateBlockMode {
    NONE,// 何も入れない
    ONLY_MINE,// 自分が関係するトランザクションのみ入れる
    FEE_SORT,// 手数料が高いトランザクションから順に
    MINE_AND_FEE_SORT,// 自分が関係するトランザクションを入れた後に手数料が高いトランザクションを入れる
}