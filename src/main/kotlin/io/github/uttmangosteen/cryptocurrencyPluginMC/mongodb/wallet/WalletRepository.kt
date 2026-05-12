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
import java.util.logging.Logger

class WalletRepository(
    database: MongoDatabase,
    private val logger: Logger
) {
    private val collection = database.getCollection<Wallet>("wallets")

    suspend fun setup() {
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

    // Walletを変更しつつ、削除したAccountなどの結果も返したい場合に使う
    suspend fun <T> computeWallet(ownerUUID: String, block: (Wallet) -> T?): T? {
        val wallet = getWallet(ownerUUID) ?: return null
        val result = block(wallet) ?: return null
        return if (save(wallet)) result
        else null
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

    suspend fun forgetAccount(ownerUUID: String, index: Int): Account? {
        // 削除したAccountは、コマンド側で公開鍵アイテム化してプレイヤーへ返す
        return computeWallet(ownerUUID) { wallet ->
            wallet.deleteAccount(index)
        }
    }

    suspend fun switchMainAccount(ownerUUID: String, index: Int): Boolean {
        // index番目のAccountとindex 0のAccountを入れ替える
        return updateWallet(ownerUUID) { wallet ->
            wallet.switchMainAccount(index)
        }
    }

    suspend fun getMainAccount(ownerUUID: String): Account? {
        // byName送金などで使用。main口座はaccounts[0]
        return getWallet(ownerUUID)?.accounts?.firstOrNull()
    }

    private suspend fun save(wallet: Wallet): Boolean {
        // ヘッダー付き公開鍵が混ざっても、統一する
        wallet.normalizeKeys()
        return try {
            val result = collection.replaceOne(
                Filters.eq("_id", wallet.ownerUUID),
                wallet,
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
            val wallet = collection.find(Filters.eq("_id", ownerUUID)).firstOrNull() ?: return null
            // ヘッダー付き公開鍵が混ざっても、統一する
            wallet.normalizeKeys()
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
}