package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.OutPoint
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Utxo
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import org.bson.Document

// Block保存用各関数Document変換関数

fun Block.toDocument(): Document {
    return Document().apply {
        append("_id", height)
        append("previousHash", previousHash)
        append("transactions", transactions.map { it.toDocument() })
        append("transactionsRoot", transactionsRoot)
        append("timestamp", timestamp)
        append("memo", memo)
        append("difficulty", difficulty)
        append("nonce", nonce)
        append("hash", hash)
    }
}

fun Document.toBlock(): Block {
    return Block(
        height = getInteger("_id"),
        previousHash = getString("previousHash"),
        transactions = getList("transactions", Document::class.java).map { it.toTransaction() },
        transactionsRoot = getString("transactionsRoot"),
        timestamp = getLong("timestamp"),
        memo = getString("memo") ?: "",
        difficulty = getLong("difficulty"),
        nonce = getLong("nonce"),
        hash = getString("hash")
    )
}

fun Transaction.toDocument(): Document {
    return Document().apply {
        append("isCoinbase", isCoinbase)
        append("inputs", inputs.map { it.toDocument() })
        append("outputs", outputs.map { it.toDocument() })
        append("timestamp", timestamp)
        append("memo", memo)
        append("txHash", txHash)
    }
}

fun Document.toTransaction(): Transaction {
    return Transaction(
        isCoinbase = getBoolean("isCoinbase") ?: false,
        inputs = getList("inputs", Document::class.java).map { it.toTxInput() },
        outputs = getList("outputs", Document::class.java).map { it.toTxOutput() },
        timestamp = getLong("timestamp"),
        memo = getString("memo") ?: "",
        txHash = getString("txHash")
    )
}

fun Transaction.TxInput.toDocument(): Document {
    return Document().apply {
        append("prevTxHash", prevTxHash)
        append("outputIndex", outputIndex)
        append("signature", signature)
        append("publicKey", publicKey)
    }
}

fun Document.toTxInput(): Transaction.TxInput {
    return Transaction.TxInput(
        prevTxHash = getString("prevTxHash"),
        outputIndex = getInteger("outputIndex"),
        signature = getString("signature"),
        publicKey = getString("publicKey")
    )
}

fun Transaction.TxOutput.toDocument(): Document {
    return Document().apply {
        append("amount", amount)
        append("receiverPubKey", receiverPubKey)
    }
}

fun Document.toTxOutput(): Transaction.TxOutput {
    return Transaction.TxOutput(
        amount = get("amount", Number::class.java).toLong(),
        receiverPubKey = getString("receiverPubKey")
    )
}

fun OutPoint.toDocument(): Document {
    return Document().apply {
        append("txHash", txHash)
        append("outputIndex", outputIndex)
    }
}

fun Document.toOutPoint(): OutPoint {
    return OutPoint(
        txHash = getString("txHash"),
        outputIndex = getInteger("outputIndex")
    )
}

fun OutPoint.toMongoId(): String {
    return "$txHash.$outputIndex"
}

fun Utxo.toDocument(): Document {
    return Document().apply {
        //検索用
        append("_id", outPoint.toMongoId())
        append("txHash", outPoint.txHash)

        append("outPoint", outPoint.toDocument())
        append("amount", amount)
        append("receiverPubKey", receiverPubKey)
        append("lockedByTxId", lockedByTxId)
    }
}

fun Document.toUtxo(): Utxo {
    return Utxo(
        outPoint = get("outPoint", Document::class.java).toOutPoint(),
        amount = get("amount", Number::class.java).toLong(),
        receiverPubKey = getString("receiverPubKey"),
        lockedByTxId = getString("lockedByTxId")
    )
}