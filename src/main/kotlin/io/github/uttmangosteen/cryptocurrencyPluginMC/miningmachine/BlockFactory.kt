package io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.CoinbasePolicy
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.DifficultyPolicy
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.repository.MempoolRepository
import java.util.concurrent.ThreadLocalRandom

class BlockFactory(
    private val plugin: Main,
    private val mempoolRepository: MempoolRepository
) {
    private val limit = plugin.pluginConfig.mempoolLimitPerBlock

    suspend fun createMiningBlock(
        latestBlock: Block,
        networkMiningPower: Long,
        machine: MiningMachine,
    ): Block? {
        val rewardAccountPubKey = machine.rewardAccountPubKey ?: return null

        val txEntries = mempoolRepository.getTxForMining(machine.createBlockMode, rewardAccountPubKey, limit)
        val transactions = txEntries.map { it.transaction }.toMutableList()

        val totalFee = txEntries.fold(0L) { sum, entry ->
            Math.addExact(sum, entry.fee)
        }

        val blockHeight = latestBlock.height + 1

        val mintedReward = CoinbasePolicy.calculateMintedReward(
            blockHeight = blockHeight,
            currentSupply = latestBlock.totalChainSupply
        )

        val totalReward = CoinbasePolicy.calculateCoinbaseAmount(
            blockHeight = blockHeight,
            currentSupply = latestBlock.totalChainSupply,
            totalFees = totalFee
        )

        val totalChainSupply = Math.addExact(latestBlock.totalChainSupply, mintedReward)

        val coinbaseTx = Transaction.createCoinbase(rewardAccountPubKey, totalReward)
        transactions.add(0, coinbaseTx)

        val previousHash = latestBlock.hash ?: return null
        val difficulty = DifficultyPolicy.calculateExpectedDifficulty(
            networkMiningPower = networkMiningPower,
            miningDelayTicks = plugin.pluginConfig.miningMachineMiningDelayTicks
        )

        return Block(
            height = blockHeight,
            previousHash = previousHash,
            transactions = transactions,
            timestamp = System.currentTimeMillis(),
            memo = machine.memo,
            difficulty = difficulty,
            totalChainSupply = totalChainSupply,
            networkMiningPower = networkMiningPower,
            nonce = ThreadLocalRandom.current().nextLong(),
            hash = null
        )
    }
}