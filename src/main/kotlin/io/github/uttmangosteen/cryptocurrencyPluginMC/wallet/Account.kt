package io.github.uttmangosteen.cryptocurrencyPluginMC.wallet

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Signer

import java.security.KeyPairGenerator

data class Account(
    val publicKey: String,
    val privateKey: String?,
    var memo: String
) {

    fun normalized(): Account {
        return Account(
            publicKey = publicKey,
            privateKey = privateKey,
            memo = memo
        )
    }

    companion object {
        fun create(): Account {
            val keyPairGenerator = KeyPairGenerator.getInstance(Signer.ALGORITHM)
            val keyPair = keyPairGenerator.generateKeyPair()

            return Account(
                publicKey = keyPair.public.encoded.toHexString(),
                privateKey = keyPair.private.encoded.toHexString(),
                memo = ""
            )
        }

        fun watchOnly(publicKey: String, memo: String = ""): Account {
            return Account(
                publicKey = publicKey,
                privateKey = null,
                memo = memo
            )
        }
    }
}