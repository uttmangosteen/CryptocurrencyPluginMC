package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block

// ブロックチェーン開始後は触らない
object DifficultyPolicy {
    const val INITIAL_DIFFICULTY: Long = 16L

    fun calculateExpectedDifficulty(latestBlock: Block): Long {
        require(latestBlock.height >= 0) { "latestBlock height must not be negative" }

        return INITIAL_DIFFICULTY
    }
}