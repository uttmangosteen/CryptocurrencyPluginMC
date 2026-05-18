package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.CoinbasePolicy
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.DifficultyPolicy
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.MiningMachine
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.mempool.MempoolRepository

class BlockFactory(
    private val plugin: Main,
    private val mempoolRepository: MempoolRepository
) {
    private val limit = plugin.pluginConfig.mempoolLimitPerBlock

    suspend fun createMiningBlock(
        latestBlock: Block,
        machine: MiningMachine,
    ): Block? {
        val rewardAccountPubKey = machine.rewardAccountPubKey ?: return null

        val txEntries = mempoolRepository.getTxForMining(machine.createBlockMode, rewardAccountPubKey, limit)
        val transactions = txEntries.map { it.transaction }.toMutableList()

        val totalFee = txEntries.fold(0L) { sum, entry ->
            Math.addExact(sum, entry.fee)
        }
        val totalReward = CoinbasePolicy.calculateCoinbaseAmount(totalFees = totalFee)
        val coinbaseTx = Transaction.createCoinbase(rewardAccountPubKey, totalReward)
        transactions.add(0, coinbaseTx)

        val previousHash = latestBlock.hash ?: return null
        val difficulty = DifficultyPolicy.calculateExpectedDifficulty(latestBlock)

        return Block(
            height = latestBlock.height + 1,
            previousHash = previousHash,
            transactions = transactions,
            timestamp = System.currentTimeMillis(),
            memo = machine.memo,
            difficulty = difficulty,
            nonce = 0L,
            hash = null
        )
    }
}