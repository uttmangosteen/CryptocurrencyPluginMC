package io.github.uttmangosteen.cryptocurrencyPluginMC.command.admin

import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.GpuConfig
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class GetGpuCommand(
    private val plugin: JavaPlugin,
    private val gpuConfig: GpuConfig
) {
    fun execute(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return
        val gpu = gpuConfig.getGpu(args[1]) ?: run { return }
        val item = gpu.createItem(plugin)
        sender.inventory.addItem(item)
        sender.sendMessage("§a${gpu.gpuName}§r§aを取得しました")
    }
}