package io.github.uttmangosteen.cryptocurrencyPluginMC.gui.miningmachine

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.TextFormat
import io.github.uttmangosteen.cryptocurrencyPluginMC.gui.CommandSuggestHelper
import io.github.uttmangosteen.cryptocurrencyPluginMC.gui.Gui
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.Items
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.MiningMachine
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.Gpu
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent

class MiningMachineGui(
    private val plugin: Main,
    initialMachine: MiningMachine
) : Gui(
    5,
    Component.text("採掘機")
        .color(NamedTextColor.DARK_GRAY)
        .decorate(TextDecoration.BOLD)
) {
    private var preventParentOpen = false
    private var machine: MiningMachine = initialMachine
    val machineId: String
        get() = machine.id

    //非同期処理によるズレ防止
    private var loadGeneration = 0

    init {
        update()
    }

    override fun open(player: Player) {
        super.open(player)
        MiningMachineGuiRegistry.register(machineId, player.uniqueId)
    }

    companion object {
        private val gpuPosIndex = listOf(3, 4, 5, 14, 23, 22, 21, 12)

        private val borderItem = Items.create(
            material = Material.GRAY_STAINED_GLASS_PANE,
            name = Component.empty()
        )
        private val spaceItem = Items.create(
            material = Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            name = Component.empty()
        )
        private val fuelItem = Items.create(
            material = Material.HOPPER,
            name = Component.text("燃料管理", NamedTextColor.GOLD, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックして燃料投入GUIを開く", NamedTextColor.YELLOW),
            )
        )
        private val userItem = Items.create(
            material = Material.PLAYER_HEAD,
            name = Component.text("ユーザー管理", NamedTextColor.AQUA, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックしてユーザーの追加/削除", NamedTextColor.YELLOW),
            )
        )
        private val recreateItem = Items.create(
            material = Material.STONECUTTER,
            name = Component.text("ブロックの再構築", NamedTextColor.AQUA, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックしてブロックを再構築", NamedTextColor.YELLOW),
                Component.text("最新のmempoolに合わせて作り直す", NamedTextColor.GRAY),
            )
        )
        private val enableItem = Items.create(
            material = Material.EMERALD_BLOCK,
            name = Component.text("電源の切り替え"),
            lore = listOf(
                Component.text("▶ クリックして電源をOFF", NamedTextColor.YELLOW),
                Component.text("現在: ", NamedTextColor.GRAY).append(Component.text("ON", NamedTextColor.GREEN))
            )
        )
        private val disableItem = Items.create(
            material = Material.REDSTONE_BLOCK,
            name = Component.text("電源の切り替え"),
            lore = listOf(
                Component.text("▶ クリックして電源をON", NamedTextColor.YELLOW),
                Component.text("現在: ", NamedTextColor.GRAY).append(Component.text("OFF", NamedTextColor.RED))
            )
        )
        private val nameShareEnableItem = Items.create(
            material = Material.BELL,
            name = Component.text("名前共有の切り替え", NamedTextColor.GOLD, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックして採掘成功時の名前共有を切り替え", NamedTextColor.YELLOW),
                Component.text("現在: ", NamedTextColor.GRAY).append(Component.text("ON", NamedTextColor.GREEN))
            )
        )
        private val nameShareDisableItem = Items.create(
            material = Material.BELL,
            name = Component.text("名前共有の切り替え", NamedTextColor.GOLD, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックして採掘成功時の名前共有を切り替え", NamedTextColor.YELLOW),
                Component.text("現在: ", NamedTextColor.GRAY).append(Component.text("OFF", NamedTextColor.RED))
            )
        )
        private val nullBlockItem = Items.create(
            material = Material.GLASS,
            name = Component.text("空のブロック", NamedTextColor.WHITE, TextDecoration.BOLD),
            lore = listOf(
                Component.text("ブロックはまだ作られていません", NamedTextColor.GRAY)
            )
        )
    }

    private fun createTxModeItem(machine: MiningMachine) = Items.create(
        material = Material.CRAFTING_TABLE,
        name = Component.text("取引収集モード切替", NamedTextColor.AQUA, TextDecoration.BOLD),
        lore = listOf(
            Component.text("▶ クリックして収集モードを切り替え", NamedTextColor.YELLOW),
            Component.text("現在: ", NamedTextColor.GRAY)
                .append(Component.text(machine.createBlockMode.name, NamedTextColor.WHITE)),
        )
    )

    private fun createRewardPubKeyItem(pubKey: String?): org.bukkit.inventory.ItemStack {
        return Items.create(
            material = Material.PAINTING,
            name = Component.text("報酬受け取り口座を設定", NamedTextColor.AQUA, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックして報酬受け取り公開鍵を設定", NamedTextColor.YELLOW),
                Component.text("現在: ", NamedTextColor.GRAY).append(Component.text(if (pubKey == null) "未設定" else TextFormat.formatKey(pubKey), NamedTextColor.WHITE))
            )
        )
    }

        private fun createSetMemoItem(memo: String?): org.bukkit.inventory.ItemStack {
            return Items.create(
                material = Material.WRITABLE_BOOK,
                name = Component.text("採掘ブロックのメモ設定", NamedTextColor.GOLD, TextDecoration.BOLD),
                lore = listOf(
                    Component.text("▶ クリックしてメモを設定", NamedTextColor.YELLOW),
                    Component.text("現在: ", NamedTextColor.GRAY)
                        .append(Component.text(memo?.ifBlank { "なし" } ?: "なし", NamedTextColor.WHITE))
                )
            )
        }

        fun loadMachine() {
            val generation = ++loadGeneration
        val targetMachineId = machineId
        plugin.launchAsync {
            val loadedMachine = plugin.miningMachineService?.getMachine(targetMachineId) ?: return@launchAsync
            plugin.runSync {
                if (generation != loadGeneration) return@runSync
                machine = loadedMachine
                update()
            }
        }
    }

    private fun update() {
        val inv = inventory
        inv.clear()
        for (i in 0 until size) setItem(i, borderItem)

        val block = machine.miningBlock
        if (block == null) setItem(13, nullBlockItem)
        else setItem(13, createBlockItem(block))

        gpuPosIndex.forEachIndexed { slotNum, slotIndex ->
            val gpu = machine.gpuSlots.getOrNull(slotNum)

            if (gpu == null) {
                setItem(slotIndex, spaceItem)
            } else {
                setItem(slotIndex, createGpuItem(slotNum, gpu))
            }
        }

        setItem(10, if (machine.enabled) enableItem else disableItem) { event ->
            val player = event.whoClicked as Player
            player.performCommand("ccmcn toggle $machineId")
        }

        setItem(16, createRewardPubKeyItem(machine.rewardAccountPubKey)) { event ->
            val player = event.whoClicked as Player
            preventParentOpen = true
            player.closeInventory()

            player.sendMessage("========================================")
            CommandSuggestHelper.send(
                player = player,
                title = "報酬の受け取り口座を設定",
                command = "/ccmcn block setRewardPubKey $machineId ",
                usage = "/ccmcn block setRewardPubKey <machineId> [index]"
            )
            player.sendMessage("========================================")
        }

        setItem(28, if (machine.shareNameOnMined) nameShareEnableItem else nameShareDisableItem) { event ->
            val player = event.whoClicked as Player
            player.performCommand("ccmcn shareName $machineId")
        }

        setItem(34, fuelItem) { event ->
            val player = event.whoClicked as Player
            preventParentOpen = true
            player.performCommand("ccmcn fuel $machineId")
        }

        setItem(37, userItem) { event ->
            val player = event.whoClicked as Player
            preventParentOpen = true
            player.closeInventory()

            player.sendMessage("========================================")
            CommandSuggestHelper.send(
                player = player,
                title = "ユーザー一覧",
                command = "/ccmcn user list $machineId",
                usage = "/ccmcn user list <machineId>"
            )
            CommandSuggestHelper.send(
                player = player,
                title = "ユーザーの追加",
                command = "/ccmcn user add $machineId ",
                usage = "/ccmcn user add <machineId> <name>"
            )
            CommandSuggestHelper.send(
                player = player,
                title = "ユーザーの削除",
                command = "/ccmcn user delete $machineId ",
                usage = "/ccmcn user delete <machineId> <name>"
            )
            player.sendMessage("========================================")
        }

        setItem(39, createSetMemoItem(machine.memo)) { event ->
            val player = event.whoClicked as Player
            preventParentOpen = true
            player.closeInventory()

            player.sendMessage("========================================")
            CommandSuggestHelper.send(
                player = player,
                title = "ブロックのメモ設定",
                command = "/ccmcn block setMemo $machineId ",
                usage = "/ccmcn block setMemo <machineId> [memo]"
            )
            player.sendMessage("========================================")
        }

        setItem(40, createTxModeItem(machine)) { event ->
            val player = event.whoClicked as Player
            player.performCommand("ccmcn block txMode $machineId")
        }

        setItem(41, recreateItem) { event ->
            val player = event.whoClicked as Player
            player.performCommand("ccmcn block recreate $machineId")
        }

        setItem(43, createFuelAmountItem(machine))
    }

    private fun createBlockItem(block: Block) = Items.create(
        material = Material.DIAMOND_ORE,
        name = Component.text("計算中ブロック情報", NamedTextColor.WHITE, TextDecoration.BOLD),
        lore = buildList {
            add(
                Component.text("ブロックの番号: ", NamedTextColor.WHITE)
                    .append(Component.text(block.height.toString(), NamedTextColor.YELLOW))
            )
            add(
                Component.text("前ブロックのハッシュ: ", NamedTextColor.WHITE)
                    .append(Component.text(TextFormat.formatKey(block.previousHash), NamedTextColor.YELLOW))
            )
            add(
                Component.text("入っている取引数: ", NamedTextColor.WHITE)
                    .append(Component.text(block.transactions.size.toString(), NamedTextColor.YELLOW))
            )
            add(
                Component.text("採掘難易度: ", NamedTextColor.WHITE)
                    .append(Component.text(block.difficulty.toString(), NamedTextColor.YELLOW))
            )
            add(
                Component.text("現在のNonce: ", NamedTextColor.WHITE)
                    .append(Component.text(block.nonce.toString(), NamedTextColor.AQUA))
            )
            add(
                Component.text("メモ: ", NamedTextColor.WHITE)
                    .append(Component.text(block.memo.ifBlank { "なし" }, NamedTextColor.GRAY))
            )
            add(
                Component.text("報酬受取公開鍵: ", NamedTextColor.WHITE)
                    .append(
                        Component.text(
                            TextFormat.formatKey(block.transactions[0].outputs[0].receiverPubKey),
                            NamedTextColor.YELLOW
                        )
                    )
            )
        }
    )

    private fun createGpuItem(slotNum: Int, gpu: Gpu) = Items.create(
        material = getGpuMaterial(gpu),
        name = if (gpu.life < 0) {
            Component.text("GPU #$slotNum - 故障", NamedTextColor.RED, TextDecoration.BOLD)
        } else if (gpu.life == 0) {
            Component.text("GPU #$slotNum - 保証稼働時間超過", NamedTextColor.YELLOW, TextDecoration.BOLD)
        } else {
            Component.text("GPU #$slotNum", NamedTextColor.AQUA, TextDecoration.BOLD)
        },
        lore = listOf(
            Items.miniMessage(gpu.gpuName),
            Component.text("計算速度: ", NamedTextColor.GRAY)
                .append(Component.text(gpu.power.toString(), getGpuLifeColor(gpu))),
            Component.text("残り寿命: ", NamedTextColor.GRAY)
                .append(Component.text(gpu.life.toString(), getGpuLifeColor(gpu))),
        )
    )

    private fun getGpuMaterial(gpu: Gpu): Material {
        if (gpu.life < 0) return Material.BARRIER

        return when (gpu.power) {
            1 -> Material.WOODEN_PICKAXE
            2 -> Material.STONE_PICKAXE
            3 -> Material.GOLDEN_PICKAXE
            4 -> Material.IRON_PICKAXE
            5 -> Material.DIAMOND_PICKAXE
            else -> Material.NETHERITE_PICKAXE
        }
    }

    private fun getGpuLifeColor(gpu: Gpu): NamedTextColor {
        return when {
            gpu.life > 0 -> NamedTextColor.GREEN
            gpu.life == 0 -> NamedTextColor.YELLOW
            else -> NamedTextColor.RED
        }
    }

    private fun createFuelAmountItem(machine: MiningMachine) = Items.create(
        material = Material.CAULDRON,
        name = Component.text("燃料総量", NamedTextColor.GOLD, TextDecoration.BOLD),
        lore = listOf(
            Component.text("現在: ", NamedTextColor.GRAY)
                .append(Component.text(machine.fuelAmount.toString(), NamedTextColor.YELLOW)),
        )
    )

    override fun onClose(e: InventoryCloseEvent): Boolean {
        MiningMachineGuiRegistry.unregister(machineId, e.player.uniqueId)
        return preventParentOpen
    }
}
