package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy

import java.math.BigInteger

// ブロックチェーン開始後は触らない
//difficulty = networkMiningPower * 20 / miningDelayTicks * 300
object DifficultyPolicy {
    const val INITIAL_DIFFICULTY: Long = 4096L

    //5分を目指す設定
    const val TARGET_BLOCK_MILLIS: Long = 5L * 60L * 1000L

    const val SERVER_TICKS_PER_SECOND: Long = 20L

    const val MIN_DIFFICULTY: Long = INITIAL_DIFFICULTY

    const val MAX_DIFFICULTY: Long = Int.MAX_VALUE.toLong()

    fun calculateExpectedDifficulty(
        networkMiningPower: Long,
        miningDelayTicks: Int
    ): Long {
        require(networkMiningPower >= 0L) { "networkMiningPower must not be negative" }
        require(miningDelayTicks > 0) { "miningDelayTicks must be positive" }

        if (networkMiningPower <= 0L) return INITIAL_DIFFICULTY

        val targetSecondsNumerator = BigInteger.valueOf(TARGET_BLOCK_MILLIS)
        val targetSecondsDenominator = BigInteger.valueOf(1000L)

        val difficulty = BigInteger.valueOf(networkMiningPower)
            .multiply(BigInteger.valueOf(SERVER_TICKS_PER_SECOND))
            .multiply(targetSecondsNumerator)
            .divide(targetSecondsDenominator)
            .divide(BigInteger.valueOf(miningDelayTicks.toLong()))

        return difficulty
            .coerceIn(BigInteger.valueOf(MIN_DIFFICULTY), BigInteger.valueOf(MAX_DIFFICULTY))
            .toLong()
    }
}