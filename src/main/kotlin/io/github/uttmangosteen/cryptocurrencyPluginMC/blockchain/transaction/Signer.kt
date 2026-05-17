package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction

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

    // 外部入力用、長さが32バイト以外か不正な文字ならnull
    fun normalizePublicKey(publicKey: String): String? {
        return runCatching {
            val bytes = publicKey.hexToByteArray()
            if (bytes.size != PUBLIC_KEY_SIZE) return null
            bytes.toHexString()
        }.getOrNull()
    }

    // 内部用、JavaのPublicKey(44バイト)から8Byteヘッダーを取り除く
    fun toRawPublicKeyHex(publicKey: PublicKey): String {
        return publicKey.encoded.takeLast(PUBLIC_KEY_SIZE).toByteArray().toHexString()
    }

    private fun decodePublicKey(publicKey: String): PublicKey {
        val bytes = publicKey.hexToByteArray()
        val specBytes = if (bytes.size == PUBLIC_KEY_SIZE) x509Header + bytes else bytes
        return keyFactory.generatePublic(X509EncodedKeySpec(specBytes))
    }

    private fun decodePrivateKey(privateKeyHex: String): PrivateKey {
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyHex.hexToByteArray()))
    }

    fun sign(privateKeyHex: String, messageHex: String): String {
        val privateKey = decodePrivateKey(privateKeyHex)
        return Signature.getInstance(ALGORITHM).apply {
            initSign(privateKey)
            update(messageHex.hexToByteArray())
        }.sign().toHexString()
    }

    fun verify(publicKey: String, messageHex: String, signatureHex: String): Boolean {
        return try {
            val publicKey = decodePublicKey(publicKey)
            Signature.getInstance(ALGORITHM).apply {
                initVerify(publicKey)
                update(messageHex.hexToByteArray())
            }.verify(signatureHex.hexToByteArray())
        } catch (e: Exception) {
            false
        }
    }
}
