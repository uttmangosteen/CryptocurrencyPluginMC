package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.OutPoint
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Utxo
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.TxInput
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.TxOutput
import io.github.uttmangosteen.cryptocurrencyPluginMC.util.decodeHex
import io.github.uttmangosteen.cryptocurrencyPluginMC.util.toHex
import org.bson.Document


//Block保存用各関数Document変換関数

fun Block.toDocument(): Document {
    val document = Document("height", height)
        .append("previousHashHex", previousHash.toHex())
        .append("transactions", transactions.map { it.toDocument() })
        .append("transactionsRoot", transactionsRoot.toHex())
        .append("timestamp", timestamp)
        .append("memo", memo)
        .append("difficulty", difficulty)
        .append("nonce", nonce)
    hash?.let { document.append("hash", it.toHex()) }
    return document
}

fun Document.toBlock(): Block {
    return Block(
        height = getInteger("height"),
        previousHash = getString("previousHashHex").decodeHex(),
        transactions = getList("transactions", Document::class.java).orEmpty().map { it.toTransaction() },
        transactionsRoot = getString("transactionsRoot").decodeHex(),
        timestamp = getLong("timestamp"),
        memo = getString("memo"),
        difficulty = getInteger("difficulty"),
        nonce = getLong("nonce") ?: 0L,
        hash = getString("hash")?.decodeHex()
    )
}

fun Transaction.toDocument(): Document {
    return Document("isCoinbase", isCoinbase)
        .append("inputs", inputs.map { input -> input.toDocument() })
        .append("outputs", outputs.map { output -> output.toDocument() })
        .append("timestamp", timestamp)
        .append("memo", memo)
        .append("txHashHex", txHash.toHex())
}

fun Document.toTransaction(): Transaction {
    val inputs = getList("inputs", Document::class.java)
        .orEmpty()
        .map { inputDocument -> inputDocument.toTxInput() }

    val outputs = getList("outputs", Document::class.java)
        .orEmpty()
        .map { outputDocument -> outputDocument.toTxOutput() }

    return Transaction(
        isCoinbase = getBoolean("isCoinbase"),
        inputs = inputs,
        outputs = outputs,
        timestamp = getLong("timestamp"),
        memo = getString("memo"),
        txHash = getString("txHashHex").decodeHex()
    )
}

fun TxInput.toDocument(): Document {
    return Document("prevTxHashHex", prevTxHash.toHex())
        .append("outputIndex", outputIndex)
        .append("signatureHex", signature?.toHex())
        .append("publicKeyHex", publicKey.toHex())
}

fun Document.toTxInput(): TxInput {
    return TxInput(
        prevTxHash = getString("prevTxHashHex").decodeHex(),
        outputIndex = getInteger("outputIndex") ?: 0,
        signature = getString("signatureHex")?.decodeHex(),
        publicKey = getString("publicKeyHex").decodeHex()
    )
}

fun TxOutput.toDocument(): Document {
    return Document("amount", amount)
        .append("receiverPubKeyHex", receiverPubKey.toHex())
}

fun Document.toTxOutput(): TxOutput {
    return TxOutput(
        amount = getLong("amount") ?: 0L,
        receiverPubKey = getString("receiverPubKeyHex").decodeHex()
    )
}

fun OutPoint.toDocument(): Document {
    return Document("txHashHex", txHashHex)
        .append("outputIndex", outputIndex)
}

fun Document.toOutPoint(): OutPoint {
    return OutPoint(
        txHashHex = getString("txHashHex") ?: "",
        outputIndex = getInteger("outputIndex") ?: 0
    )
}

fun OutPoint.toMongoId(): String {
    return "$txHashHex.$outputIndex"
}

fun Utxo.toDocument(): Document {
    val document = Document("_id", outPoint.toMongoId())
        .append("txHashHex", txHash.toHex())
        .append("amount", amount)
        .append("receiverPubKeyHex", receiverPubKeyHex)

    if (lockedByTxId != null) {
        document.append("lockedByTxId", lockedByTxId)
    }

    return document
}

fun Document.toUtxo(): Utxo {
    val id = getString("_id")
    val txHashHex = getString("txHashHex")
    val receiverPubKeyHex = getString("receiverPubKeyHex")

    return Utxo(
        outPoint = OutPoint(
            txHashHex = id.substringBeforeLast("."),
            outputIndex = id.substringAfterLast(".").toInt()
        ),
        txHash = txHashHex.decodeHex(),
        amount = getLong("amount") ?: 0L,
        receiverPubKey = receiverPubKeyHex.decodeHex(),
        receiverPubKeyHex = receiverPubKeyHex,
        lockedByTxId = getString("lockedByTxId")
    )
}