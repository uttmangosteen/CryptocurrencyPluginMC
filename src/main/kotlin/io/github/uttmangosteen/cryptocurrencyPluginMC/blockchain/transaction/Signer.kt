package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

object Signer {
    const val ALGORITHM = "Ed25519"

    private val x509Header = byteArrayOf(
        0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00
    )

    private val keyFactory: KeyFactory = KeyFactory.getInstance(ALGORITHM)

    fun decodePublicKey(publicKeyBytes: ByteArray): PublicKey {
        val encodedBytes = if (publicKeyBytes.size == 32) {
            x509Header + publicKeyBytes
        } else {
            publicKeyBytes
        }
        return keyFactory.generatePublic(X509EncodedKeySpec(encodedBytes))
    }

    fun decodePrivateKey(privateKeyBytes: ByteArray): PrivateKey {
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
    }

    fun sign(privateKeyBytes: ByteArray, data: ByteArray): ByteArray {
        val privateKey = decodePrivateKey(privateKeyBytes)

        return Signature.getInstance(ALGORITHM).apply {
            initSign(privateKey)
            update(data)
        }.sign()
    }

    fun verify(publicKeyBytes: ByteArray, data: ByteArray, signatureBytes: ByteArray): Boolean {
        val publicKey = decodePublicKey(publicKeyBytes)

        return Signature.getInstance(ALGORITHM).run {
            initVerify(publicKey)
            update(data)
            verify(signatureBytes)
        }
    }
}
