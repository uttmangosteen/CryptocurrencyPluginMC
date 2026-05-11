package io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine

import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.miningmachine.MiningMachineRepository

// マシン全体を管理するサービスクラス
// マシンの作成、GPU管理、燃料管理、マイニングのtick処理など
class MiningMachineService(
    private val miningMachineRepository: MiningMachineRepository,

) {
}