package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain

// UTXO を一意に識別するための参照情報
data class OutPoint(
    val txHashHex: String,
    val outputIndex: Int
)
