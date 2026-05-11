package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction

import io.github.uttmangosteen.cryptocurrencyPluginMC.util.decodeHex
import io.github.uttmangosteen.cryptocurrencyPluginMC.util.toHex
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

object Signer {
    const val ALGORITHM = "Ed25519"

    const val PUBLIC_KEY_SIZE = 32

    private val x509Header = byteArrayOf(
        0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00
    )

    private val keyFactory: KeyFactory = KeyFactory.getInstance(ALGORITHM)

    fun normalizePublicKeyBytes(publicKeyBytes: ByteArray): ByteArray {
        require(publicKeyBytes.size >= PUBLIC_KEY_SIZE) {
            "public key must be at least $PUBLIC_KEY_SIZE bytes"
        }
        if (publicKeyBytes.size == PUBLIC_KEY_SIZE) return publicKeyBytes
        return publicKeyBytes.takeLast(PUBLIC_KEY_SIZE).toByteArray()
    }

    fun normalizePublicKeyHex(publicKeyHex: String): String {
        if (publicKeyHex.length == PUBLIC_KEY_SIZE * 2) return publicKeyHex.lowercase()
        return normalizePublicKeyBytes(publicKeyHex.decodeHex()).toHex()
    }

    fun encodePublicKey(publicKeyBytes: ByteArray): ByteArray {
        if (publicKeyBytes.size == PUBLIC_KEY_SIZE) return x509Header + publicKeyBytes
        return publicKeyBytes
    }

    fun decodePublicKey(publicKeyBytes: ByteArray): PublicKey {
        return keyFactory.generatePublic(X509EncodedKeySpec(encodePublicKey(publicKeyBytes)))
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
