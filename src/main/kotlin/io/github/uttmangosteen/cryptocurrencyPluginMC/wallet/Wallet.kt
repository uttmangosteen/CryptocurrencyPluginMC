package io.github.uttmangosteen.cryptocurrencyPluginMC.wallet

class Wallet(
    val ownerUUID: String,
    val accounts: MutableList<Account> //index:0の口座がmain口座
) {
    fun createAccount(): Boolean {
        if (accounts.size >= MAX_ACCOUNTS) return false
        val account = Account.create()
        accounts.add(account)
        return true
    }

    fun addAccount(account: Account): Boolean {
        if (accounts.size >= MAX_ACCOUNTS) return false
        if (accounts.any { it.publicKey == account.publicKey }) return false
        accounts.add(account)
        return true
    }

    fun deleteAccount(index: Int): Boolean {
        if (index !in accounts.indices) return false
        accounts.removeAt(index)
        return true
    }

    fun switchMainAccount(index: Int): Boolean {
        if (index == 0) return true
        if (index !in accounts.indices) return false
        val mainAccount = accounts[index]
        accounts[index] = accounts[0]
        accounts[0] = mainAccount
        return true
    }

    fun updateMemo(index: Int, memo: String): Boolean {
        if (index !in accounts.indices) return false
        accounts[index].memo = memo
        return true
    }

    companion object {
        private const val MAX_ACCOUNTS = 8

        fun create(ownerUUID: String): Wallet {
            return Wallet(
                ownerUUID = ownerUUID,
                accounts = mutableListOf()
            )
        }
    }
}