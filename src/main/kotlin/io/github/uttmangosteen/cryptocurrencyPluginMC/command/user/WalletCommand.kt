package io.github.uttmangosteen.cryptocurrencyPluginMC.command.user

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.gui.wallet.WalletGui
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Wallet
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class WalletCommand(
    private val plugin: Main
) {
    fun execute(sender: CommandSender) {
        if (sender !is Player) return

        plugin.launchAsync {
            val wallet = plugin.repositories.walletRepo.getWallet(sender.uniqueId.toString())
                ?: Wallet.create(sender.uniqueId.toString())

            plugin.runSync {
                WalletGui(wallet).open(sender)
            }
        }
    }
}