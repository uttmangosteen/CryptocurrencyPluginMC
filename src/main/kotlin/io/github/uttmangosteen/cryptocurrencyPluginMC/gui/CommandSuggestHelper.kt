package io.github.uttmangosteen.cryptocurrencyPluginMC.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player

object CommandSuggestHelper {
    private fun command(command: String): Component {
        return Component.text("[ここをクリック]", NamedTextColor.YELLOW, TextDecoration.BOLD, TextDecoration.UNDERLINED)
            .clickEvent(ClickEvent.suggestCommand(command))
    }

    fun send(
        player: Player,
        title: String,
        command: String,
        usage: String
    ) {
        player.sendMessage(Component.text("$title=> ").append(command(command)))
        player.sendMessage("§7$usage")
    }
}