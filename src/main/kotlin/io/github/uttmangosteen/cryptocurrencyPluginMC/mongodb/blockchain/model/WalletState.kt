package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.model

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Utxo

data class WalletState(
    val availableUtxos: List<Utxo>,
    val pendingUtxos: List<Utxo>,
    val balance: Long,
    val pendingBalance: Long,
    val totalBalance: Long
)