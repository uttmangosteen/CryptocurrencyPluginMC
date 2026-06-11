package io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.gui.miningmachine.MachineFuelGui
import org.bukkit.Sound
import org.bukkit.entity.Player

class MachineFuelCommand(
    private val plugin: Main
) {
    fun execute(player: Player, args: Array<out String>) {
        val machineId = args.getOrNull(1) ?: return

        val gui = MachineFuelGui(plugin, machineId)
        player.playSound(
            player.location,
            Sound.BLOCK_ENDER_CHEST_OPEN,
            1.0f,
            1.0f
        )
        gui.open(player)
    }
}