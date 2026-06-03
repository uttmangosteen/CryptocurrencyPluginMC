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

    //Coinbase,Difficulty計算単純化のための要素
    val totalChainSupply: Long,
    val networkMiningPower: Long,

    var nonce: Long = 0,
    var hash: String? = null
) {
    private val headerBytes = prepareHeaderBytes()

    val targetBytes = calculateTargetBytes(this.difficulty)

    companion object {
        private const val MEMO_MAX_LENGTH = 32

        // SHA-256の最大値(2^256 - 1)
        val MAX_HASH_VALUE: BigInteger = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)

        private fun calculateTransactionsRoot(txs: List<Transaction>): String {
            if (txs.isEmpty()) return ByteArray(32).toHexString()
            val buffer = ByteBuffer.allocate(txs.size * 32)
            txs.forEach { tx -> buffer.put(tx.txHash.hexToByteArray()) }
            return buffer.array().sha256().toHexString()
        }

        fun calculateTargetBytes(diff: Long): ByteArray {
            if (diff <= 0) return ByteArray(32) { 0 }
            val target = MAX_HASH_VALUE.divide(BigInteger.valueOf(diff))
            val targetBytes = target.toByteArray()
            val result = ByteArray(32)
            if (targetBytes.size == 33) {
                System.arraycopy(targetBytes, 1, result, 0, 32)
            } else if (targetBytes.size <= 32) {
                System.arraycopy(targetBytes, 0, result, 32 - targetBytes.size, targetBytes.size)
            }
            return result
        }

        // 1/diffで採掘成功
        fun isMined(hashBytes: ByteArray, targetBytes: ByteArray): Boolean {
            for (i in 0 until 32) {
                val h = hashBytes[i].toInt() and 0xFF
                val t = targetBytes[i].toInt() and 0xFF
                if (h < t) return true
                if (h > t) return false
            }
            return true
        }

        fun createGenesis(): Block {
            val genesisBlock = Block(
                height = 0,
                previousHash = "00".repeat(32),
                transactions = emptyList(),
                timestamp = System.currentTimeMillis(),
                memo = "Genesis Block",
                difficulty = 1L,
                totalChainSupply = 0L,
                networkMiningPower = 0L,
                nonce = 0L,
                hash = null
            )
            //絶対採掘成功
            genesisBlock.tryMine()
            return genesisBlock
        }
    }

    private fun prepareHeaderBytes(): ByteArray {
        val safeMemo = memo.take(MEMO_MAX_LENGTH)
        val memoBytes = safeMemo.toByteArray(Charsets.UTF_8)

        val prevHashBytes = previousHash.hexToByteArray()
        val rootBytes = transactionsRoot.hexToByteArray()

        val totalSize = 4 + prevHashBytes.size + rootBytes.size + 8 + memoBytes.size + 8 + 8 + 8
        val buffer = ByteBuffer.allocate(totalSize)
            .putInt(height)
            .put(prevHashBytes)
            .put(rootBytes)
            .putLong(timestamp)
            .put(memoBytes)
            .putLong(difficulty)
            .putLong(totalChainSupply)
            .putLong(networkMiningPower)

        return buffer.array()
    }



    //自身のnonceを探す
    fun tryMine(times: Int = 1): Boolean {
        if (times <= 0) return false

        val digest = localDigest.get()

        val nonceBytes = ByteArray(8)
        val hashResult = ByteArray(32)
        var currentNonce = this.nonce

        repeat(times) {
            digest.reset()
            digest.update(headerBytes)

            currentNonce.writeTo(nonceBytes)

            digest.update(nonceBytes)
            digest.digest(hashResult, 0, 32)

            if (isMined(hashResult, targetBytes)) {
                this.nonce = currentNonce
                this.hash = hashResult.toHexString()
                return true
            }
            currentNonce++
        }

        this.nonce = currentNonce
        return false
    }


    private fun calculateHash(targetNonce: Long): ByteArray {
        val digest = localDigest.get()
        digest.reset()
        digest.update(headerBytes)

        val nonceBytes = ByteArray(8)
        targetNonce.writeTo(nonceBytes)

        digest.update(nonceBytes)
        return digest.digest()
    }

    //Block自身でできるチェックしか含まれていない点に注意
    fun isValid(latestBlock: Block, expectedDifficulty: Long): Boolean {
        if (this.height != latestBlock.height + 1) return false
        if (this.previousHash != latestBlock.hash) return false
        if (this.difficulty != expectedDifficulty) return false
        if (this.totalChainSupply < latestBlock.totalChainSupply) return false
        if (this.networkMiningPower < 0L) return false

        if (this.timestamp <= latestBlock.timestamp) return false

        // hash
        val blockHash = this.hash ?: return false
        val calculatedHashBytes = calculateHash(this.nonce)
        if (blockHash != calculatedHashBytes.toHexString()) return false

        //　hash がクリアできてるか
        if (!isMined(calculatedHashBytes, targetBytes)) return false

        if (transactions.isEmpty()) return false

        // txRoot
        if (this.transactionsRoot != calculateTransactionsRoot(this.transactions)) return false

        // Coinbase
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

//Long(64)→Byte(8)Array変換
private fun Long.writeTo(dest: ByteArray) {
    dest[0] = (this ushr 56).toByte()
    dest[1] = (this ushr 48).toByte()
    dest[2] = (this ushr 40).toByte()
    dest[3] = (this ushr 32).toByte()
    dest[4] = (this ushr 24).toByte()
    dest[5] = (this ushr 16).toByte()
    dest[6] = (this ushr 8).toByte()
    dest[7] = this.toByte()
}
