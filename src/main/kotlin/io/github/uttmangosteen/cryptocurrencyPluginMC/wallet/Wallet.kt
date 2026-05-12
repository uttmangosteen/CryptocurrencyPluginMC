package io.github.uttmangosteen.cryptocurrencyPluginMC.wallet

import org.bson.codecs.pojo.annotations.BsonId

class Wallet(
    @BsonId //PRIMARY_KEY
    val ownerUUID: String,
    //index:0の口座がmain口座
    val accounts: MutableList<Account>
) {
    //TODO:GUI設計時要調整
    private val maxAccounts = 6

    fun normalizeKeys() {
        accounts.replaceAll { account -> account.normalized() }
    }

    fun createAccount(): Boolean {
        if (accounts.size >= maxAccounts) return false
        val account = Account.create()
        accounts.add(account)
        return true
    }

    fun addAccount(account: Account): Boolean {
        if (accounts.size >= maxAccounts) return false
        accounts.add(account)
        return true
    }

    fun deleteAccount(index: Int): Account? {
        if (index !in accounts.indices) return null
        val account = accounts[index]
        accounts.removeAt(index)
        return account
    }

    fun switchMainAccount(index: Int): Boolean {
        if (index == 0) return true
        if (index !in accounts.indices) return false
        val mainAccount = accounts[index]
        accounts[index] = accounts[0]
        accounts[0] = mainAccount
        return true
    }

    companion object {
        fun create(ownerUUID: String): Wallet {
            return Wallet(
                ownerUUID = ownerUUID,
                accounts = mutableListOf()
            )
        }
    }
}