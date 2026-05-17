package io.github.uttmangosteen.cryptocurrencyPluginMC.wallet

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Signer

import java.security.KeyPairGenerator

data class Account(
    val publicKey: String,
    val privateKey: String?,
    var memo: String
) {
    companion object {
        fun create(): Account {
            val keyPairGenerator = KeyPairGenerator.getInstance(Signer.ALGORITHM)
            val keyPair = keyPairGenerator.generateKeyPair()

            return Account(
                publicKey = Signer.toRawPublicKeyHex(keyPair.public),
                privateKey = keyPair.private.encoded.toHexString(),
                memo = ""
            )
        }

        fun watchOnly(publicKey: String, memo: String = ""): Account? {
            val normalizedKey = Signer.normalizePublicKey(publicKey) ?: return null
            return Account(
                publicKey = normalizedKey,
                privateKey = null,
                memo = memo
            )
        }
    }
}