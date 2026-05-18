package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy

// ブロックチェーン開始後は触らない
object CoinbasePolicy {
    const val COIN_SCALE: Long = 100_000L

    const val BASE_REWARD: Long = 10L * COIN_SCALE

    const val MAX_SUPPLY: Long = 2_000_000L * COIN_SCALE

    // 何ブロックごとに半減期？ 0以下なら半減なし
    const val HALVING_INTERVAL: Int = 100000

    const val COIN_NAME: String = "MNC"

    fun calculateCoinbaseAmount(
        blockHeight: Int,
        currentSupply: Long,
        totalFees: Long
    ): Long {
        require(totalFees >= 0L) { "totalFees must not be negative" }

        val mintedReward = calculateMintedReward(
            blockHeight = blockHeight,
            currentSupply = currentSupply
        )

        return Math.addExact(mintedReward, totalFees)
    }

    fun calculateMintedReward(
        blockHeight: Int,
        currentSupply: Long
    ): Long {
        require(blockHeight >= 0) { "blockHeight must not be negative" }
        require(currentSupply >= 0L) { "currentSupply must not be negative" }

        if (blockHeight == 0) return 0L

        val rawReward = calculateRawMintedReward(blockHeight)
        if (rawReward <= 0L) return 0L

        val remainingSupply = MAX_SUPPLY - currentSupply
        return if (remainingSupply <= 0L) {
            0L
        } else {
            minOf(rawReward, remainingSupply)
        }
    }

    fun calculateRawMintedReward(blockHeight: Int): Long {
        require(blockHeight >= 0) { "blockHeight must not be negative" }

        if (blockHeight == 0) return 0L
        if (HALVING_INTERVAL <= 0) return BASE_REWARD

        val halvings = (blockHeight - 1) / HALVING_INTERVAL
        if (halvings >= Long.SIZE_BITS - 1) return 0L

        return BASE_REWARD shr halvings
    }
}