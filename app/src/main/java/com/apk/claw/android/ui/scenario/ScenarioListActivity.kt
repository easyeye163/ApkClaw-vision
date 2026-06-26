package com.apk.claw.android.ui.scenario

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.apk.claw.android.R
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.ui.chat.ChatActivity
import com.apk.claw.android.ui.fpv.FPVGameActivity
import com.apk.claw.android.widget.CommonToolbar

/**
 * 应用场景列表页
 *
 * 展示 9 个预定义的大模型应用场景，点击后跳转聊天界面。
 * 支持选择使用本地模型或远程模型。
 */
class ScenarioListActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scenario_list)

        val toolbar = findViewById<CommonToolbar>(R.id.toolbar)
        toolbar.setTitle(getString(R.string.scenario_title))
        toolbar.showBackButton(true) { finish() }

        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvScenarios)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = ScenarioAdapter(Scenario.ALL) { scenario ->
            if (scenario.isFPVGame) {
                // 灵境 FPV 飞行游戏：直接启动游戏，无需选择模型
                startActivity(Intent(this, FPVGameActivity::class.java))
            } else {
                showModelChoiceDialog(scenario)
            }
        }
    }

    /**
     * 弹出选择对话框：本地模型 / 远程模型
     */
    private fun showModelChoiceDialog(scenario: Scenario) {
        val items = arrayOf(
            getString(R.string.scenario_use_local),
            getString(R.string.scenario_use_remote)
        )

        AlertDialog.Builder(this)
            .setTitle(scenario.title)
            .setItems(items) { _, which ->
                val useLocalModel = (which == 0)
                launchChat(scenario, useLocalModel)
            }
            .show()
    }

    /**
     * 跳转到 ChatActivity，携带场景 system prompt 和模型模式
     */
    private fun launchChat(scenario: Scenario, useLocalModel: Boolean) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra(ChatActivity.EXTRA_SCENARIO_SYSTEM_PROMPT, scenario.systemPrompt)
        intent.putExtra(ChatActivity.EXTRA_SCENARIO_NAME, scenario.title)
        intent.putExtra(ChatActivity.EXTRA_SCENARIO_USE_LOCAL, useLocalModel)
        intent.putExtra(ChatActivity.EXTRA_SCENARIO_SUPPORTS_IMAGE, scenario.supportsImage)

        if (scenario.supportsImage) {
            Toast.makeText(this, getString(R.string.scenario_image_hint), Toast.LENGTH_SHORT).show()
        }

        startActivity(intent)
    }
}