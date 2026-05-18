package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy

//ブロックチェーン開始後は触らない
object CoinbasePolicy {
    const val COIN_SCALE: Long = 100_000L

    const val BASE_REWARD: Long = 10L * COIN_SCALE

    const val MAX_SUPPLY: Long = 100_000_000L * COIN_SCALE

    fun calculateCoinbaseAmount(
        totalFees: Long,
        currentSupply: Long
    ): Long {
        require(totalFees >= 0L) { "totalFees must not be negative" }
        require(currentSupply >= 0L) { "currentSupply must not be negative" }

        val remainingSupply = MAX_SUPPLY - currentSupply
        val mintedReward = if (remainingSupply <= 0L) {
            0L
        } else {
            minOf(BASE_REWARD, remainingSupply)
        }

        return Math.addExact(mintedReward, totalFees)
    }
}