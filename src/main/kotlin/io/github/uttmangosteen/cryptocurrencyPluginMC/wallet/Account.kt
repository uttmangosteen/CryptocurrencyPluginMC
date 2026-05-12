package io.github.uttmangosteen.cryptocurrencyPluginMC.wallet

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Signer
import io.github.uttmangosteen.cryptocurrencyPluginMC.util.decodeHex
import io.github.uttmangosteen.cryptocurrencyPluginMC.util.toHex
import java.security.KeyPairGenerator

data class Account(
    val publicKeyHex: String,
    val privateKeyHex: String,
    var memo: String
) {
    fun publicKeyBytes(): ByteArray {
        return publicKeyHex.decodeHex()
    }

    fun privateKeyBytes(): ByteArray? {
        return privateKeyHex.decodeHex()
    }

    fun normalized(): Account {
        return Account(
            publicKeyHex = Signer.normalizePublicKeyHex(publicKeyHex),
            privateKeyHex = privateKeyHex,
            memo = memo
        )
    }

    companion object {
        fun create(): Account {
            val keyPairGenerator = KeyPairGenerator.getInstance(Signer.ALGORITHM)
            val keyPair = keyPairGenerator.generateKeyPair()

            return Account(
                publicKeyHex = Signer.normalizePublicKeyBytes(keyPair.public.encoded).toHex(),
                privateKeyHex = keyPair.private.encoded.toHex(),
                memo = ""
            )
        }
    }
}