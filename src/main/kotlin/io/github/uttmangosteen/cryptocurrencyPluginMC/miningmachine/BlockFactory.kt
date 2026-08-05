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

    private var cachedMempoolRevision = Long.MIN_VALUE
    private val transactionSelections = mutableMapOf<SelectionKey, TransactionSelection>()

    suspend fun createMiningBlock(
        latestBlock: Block,
        networkMiningPower: Long,
        machine: MiningMachine,
    ): Block? {
        val rewardAccountPubKey = machine.rewardAccountPubKey ?: return null

        val selection = getTransactionSelection(machine.createBlockMode, rewardAccountPubKey)
        val transactions = ArrayList<Transaction>(selection.transactions.size + 1)
        transactions.addAll(selection.transactions)

        val blockHeight = latestBlock.height + 1

        val mintedReward = CoinbasePolicy.calculateMintedReward(
            blockHeight = blockHeight,
            currentSupply = latestBlock.totalChainSupply
        )

        val totalReward = CoinbasePolicy.calculateCoinbaseAmount(
            blockHeight = blockHeight,
            currentSupply = latestBlock.totalChainSupply,
            totalFees = selection.totalFee
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

    private suspend fun getTransactionSelection(
        mode: CreateBlockMode,
        minerPubKey: String
    ): TransactionSelection {
        if (mode == CreateBlockMode.NONE) return TransactionSelection.EMPTY

        val mempoolRevision = mempoolRepository.miningSelectionRevision()
        if (cachedMempoolRevision != mempoolRevision) {
            transactionSelections.clear()
            cachedMempoolRevision = mempoolRevision
        }

        val key = SelectionKey(
            mode = mode,
            minerPubKey = when (mode) {
                CreateBlockMode.FEE_SORT -> null
                else -> minerPubKey
            }
        )
        transactionSelections[key]?.let { return it }

        val entries = mempoolRepository.getTxForMining(mode, minerPubKey, limit)
        return TransactionSelection(
            transactions = entries.map { it.transaction },
            totalFee = entries.fold(0L) { sum, entry -> Math.addExact(sum, entry.fee) }
        ).also { transactionSelections[key] = it }
    }

    private data class SelectionKey(
        val mode: CreateBlockMode,
        val minerPubKey: String?
    )

    private data class TransactionSelection(
        val transactions: List<Transaction>,
        val totalFee: Long
    ) {
        companion object {
            val EMPTY = TransactionSelection(emptyList(), 0L)
        }
    }
}
