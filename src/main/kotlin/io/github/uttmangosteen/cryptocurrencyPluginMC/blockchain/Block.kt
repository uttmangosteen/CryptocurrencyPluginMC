package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import java.math.BigInteger
import java.nio.ByteBuffer

data class Block(
    val height: Int,
    val previousHash: String,
    val transactions: List<Transaction>,
    val transactionsRoot: String = calculateTransactionsRoot(transactions),
    val timestamp: Long,
    val memo: String = "",
    val difficulty: Long,
    var nonce: Long = 0,
    var hash: String? = null
) {
    private val headerBytes = prepareHeaderBytes()

    fun calculateHash(nonce: Long): ByteArray {
        val buffer = ByteBuffer.allocate(this.headerBytes.size + 8)
            .put(this.headerBytes)
            .putLong(nonce)
        return buffer.array().sha256()
    }

    companion object {
        private const val MEMO_MAX_LENGTH = 32

        // SHA-256の最大値(2^256 - 1)
        val MAX_HASH_VALUE: BigInteger = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)

        private fun calculateTransactionsRoot(txs: List<Transaction>): String {
            if (txs.isEmpty()) return ByteArray(32).toHexString()
            val buffer = ByteBuffer.allocate(txs.size * 32)
            txs.forEach { tx ->
                buffer.put(tx.txHash.hexToByteArray())
            }
            return buffer.array().sha256().toHexString()
        }

        // 1/diffで採掘成功
        fun isMined(hashBytes: ByteArray, diff: Long): Boolean {
            if (diff <= 0) return false
            val hashValue = BigInteger(1, hashBytes)
            val target = MAX_HASH_VALUE.divide(BigInteger.valueOf(diff))
            return hashValue <= target
        }
    }

    private fun prepareHeaderBytes(): ByteArray {
        val safeMemo = memo.take(MEMO_MAX_LENGTH)
        val memoBytes = safeMemo.toByteArray(Charsets.UTF_8)

        val prevHashBytes = previousHash.hexToByteArray()
        val rootBytes = transactionsRoot.hexToByteArray()

        val totalSize = 4 + prevHashBytes.size + rootBytes.size + 8 + memoBytes.size + 8
        val buffer = ByteBuffer.allocate(totalSize)
            .putInt(height)
            .put(prevHashBytes)
            .put(rootBytes)
            .putLong(timestamp)
            .put(memoBytes)
            .putLong(difficulty)

        return buffer.array()
    }

    //Block自身でできるチェックしか含まれていない点に注意されたい
    fun isValid(latestBlock: Block, expectedDifficulty: Long): Boolean {
        if (this.height != latestBlock.height + 1) return false
        if (this.previousHash != latestBlock.hash) return false
        if (this.difficulty != expectedDifficulty) return false

        if (this.timestamp <= latestBlock.timestamp) return false

        // hash
        val blockHash = this.hash ?: return false
        val calculatedHashBytes = calculateHash(this.nonce)
        if (blockHash != calculatedHashBytes.toHexString()) return false

        //　hash がクリアできてるか
        if (!isMined(calculatedHashBytes, difficulty)) return false

        if (transactions.isEmpty()) return false

        // txRoot
        if (this.transactionsRoot != calculateTransactionsRoot(this.transactions)) return false

        //coinbase
        val firstTx = transactions[0]
        if (!firstTx.isCoinbase) return false
        if (!firstTx.isValid()) return false

        // tx
        val spentOutpoints = mutableSetOf<OutPoint>()
        for (i in 1 until transactions.size) {
            val tx = transactions[i]
            if (tx.isCoinbase) return false
            if (!tx.isValid()) return false

            for (input in tx.inputs) {
                val outpoint = OutPoint(
                    txHash = input.prevTxHash,
                    outputIndex = input.outputIndex
                )
                //同じブロック内でのutxo2重使用?
                if (!spentOutpoints.add(outpoint)) return false
            }
        }
        return true
    }
}
