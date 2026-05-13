package io.github.uttmangosteen.cryptocurrencyPluginMC.command.admin

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.GpuConfig
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class GetGpuCommand(
    private val plugin: Main,
    private val gpuConfig: GpuConfig
) {
    private val prefix = plugin.pluginConfig.prefix

    fun execute(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return
        if (args.size < 2) {
            sender.sendMessage("${prefix}§cGPU IDを指定してください")
            return
        }

        val gpu = gpuConfig.getGpu(args[1]) ?: run {
            sender.sendMessage("${prefix}§c指定されたGPUが見つかりません")
            return
        }

        val item = gpu.createItem(plugin)
        sender.inventory.addItem(item)
        sender.sendMessage("${prefix}§a${gpu.gpuName} §r§aを取得しました")
    }
}