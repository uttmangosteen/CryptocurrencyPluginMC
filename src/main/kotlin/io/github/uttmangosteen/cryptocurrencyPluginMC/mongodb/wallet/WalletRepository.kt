package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.wallet

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Account
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Wallet
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import java.util.logging.Logger

class WalletRepository(
    database: MongoDatabase,
    private val logger: Logger
) {
    private val collection = database.getCollection<Document>("wallets")

    fun setup() {
        // walletsはownerUUIDを_idとして保存するため、追加Indexは不要
        logger.ccInfo(LogComponent.WALLET_REPOSITORY, "setup completed")
    }

    // Walletを取得して、渡された処理で変更し、trueが返った場合のみ保存する
    suspend fun updateWallet(ownerUUID: String, block: (Wallet) -> Boolean): Boolean {
        val wallet = getWallet(ownerUUID) ?: return false
        val shouldSave = block(wallet)
        if (!shouldSave) return false
        return save(wallet)
    }

    suspend fun getWallet(ownerUUID: String): Wallet? {
        // Walletを一度も作っていないプレイヤーの場合はnull
        return get(ownerUUID)
    }

    suspend fun createAccount(ownerUUID: String): Boolean? {
        val wallet = getWallet(ownerUUID) ?: Wallet.create(ownerUUID)
        val created = wallet.createAccount()
        if (!created) return false
        val saved = save(wallet)
        if (!saved) return null
        return true
    }

    suspend fun registerAccount(ownerUUID: String, account: Account): Boolean? {
        val wallet = getWallet(ownerUUID) ?: Wallet.create(ownerUUID)
        val registered = wallet.addAccount(account)
        if (!registered) return false
        val saved = save(wallet)
        if (!saved) return null
        return true
    }

    suspend fun forgetAccount(ownerUUID: String, index: Int): Boolean {
        return updateWallet(ownerUUID) { wallet ->
            wallet.deleteAccount(index)
        }
    }

    suspend fun switchMainAccount(ownerUUID: String, index: Int): Boolean {
        // index番目のAccountとindex 0のAccountを入れ替える
        return updateWallet(ownerUUID) { wallet ->
            wallet.switchMainAccount(index)
        }
    }

    suspend fun updateMemo(ownerUUID: String, index: Int, memo: String): Boolean {
        return updateWallet(ownerUUID) { wallet ->
            wallet.updateMemo(index, memo)
        }
    }

    suspend fun getMainAccount(ownerUUID: String): Account? {
        // byName送金などで使用。main口座はaccounts[0]
        return getWallet(ownerUUID)?.accounts?.firstOrNull()
    }

    private suspend fun save(wallet: Wallet): Boolean {
        return try {
            val result = collection.replaceOne(
                Filters.eq("_id", wallet.ownerUUID),
                wallet.toDocument(),
                ReplaceOptions().upsert(true)
            )
            result.wasAcknowledged()
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.WALLET_REPOSITORY,
                "failed to save wallet",
                e,
                "ownerUUID" to wallet.ownerUUID
            )
            false
        }
    }

    private suspend fun get(ownerUUID: String): Wallet? {
        return try {
            val document = collection.find(Filters.eq("_id", ownerUUID)).firstOrNull() ?: return null
            val wallet = document.toWallet()
            wallet
        } catch (e: Exception) {
            logger.ccWarning(
                LogComponent.WALLET_REPOSITORY,
                "failed to get wallet",
                e,
                "ownerUUID" to ownerUUID
            )
            null
        }
    }

    private fun Wallet.toDocument(): Document {
        return Document("_id", ownerUUID)
            .append(
                "accounts",
                accounts.map { account ->
                    Document("publicKeyHex", account.publicKey)
                        .append("privateKeyHex", account.privateKey)
                        .append("memo", account.memo)
                }
            )
    }

    private fun Document.toWallet(): Wallet {
        val ownerUUID = getString("_id")
        val accounts = getList("accounts", Document::class.java)
            .orEmpty()
            .map { accountDocument ->
                Account(
                    publicKey = accountDocument.getString("publicKeyHex"),
                    privateKey = accountDocument.getString("privateKeyHex"),
                    memo = accountDocument.getString("memo") ?: ""
                ).normalized()
            }
            .toMutableList()

        return Wallet(
            ownerUUID = ownerUUID,
            accounts = accounts
        )
    }
}