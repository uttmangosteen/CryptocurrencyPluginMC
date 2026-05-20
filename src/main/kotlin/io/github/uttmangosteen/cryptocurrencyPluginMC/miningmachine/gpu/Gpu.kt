package io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu

import java.util.concurrent.ThreadLocalRandom

data class Gpu(
    var gpuName: String, // GPU名 gpu.ymlのnameの部分
    var material: String,
    var customModelData: Float = 0f,
    var description: String,
    var life: Int, // 耐久値 (0=寿命切れ、-1=壊れた)
    var breakChance: Double, // 耐久値が0のときに掘れる確率 (0.0〜1.0)
    var power: Int, // このGPUが掘る度回せるnonce数
) {
    // GPU が採掘可能か
    fun isActive(): Boolean {
        return life >= 0
    }

    // 掘る度呼ばれる耐久値の消費処理
    fun consumeLife() {
        if (!isActive()) return
        if (life != 0) {
            life--
            return
        }
        if (ThreadLocalRandom.current().nextDouble() < breakChance) life = -1
    }
}