package com.apk.claw.android.tool

import com.apk.claw.android.tool.impl.*
import com.apk.claw.android.tool.impl.mobile.*
import com.apk.claw.android.tool.impl.tv.*

import com.apk.claw.android.tool.impl.fpv.*

object ToolRegistry {

    enum class DeviceType { TV, MOBILE }

    private val tools = LinkedHashMap<String, BaseTool>()
    var deviceType: DeviceType = DeviceType.TV
        private set

    @JvmStatic
    fun getInstance(): ToolRegistry = this

    fun registerAllTools(type: DeviceType = DeviceType.TV) {
        deviceType = type
        tools.clear()
        registerCommonTools()
        when (type) {
            DeviceType.TV -> registerTvTools()
            DeviceType.MOBILE -> registerMobileTools()
        }
    }

    private fun registerCommonTools() {
        register(GetScreenInfoTool())
        register(FindNodeInfoTool())
        register(InputTextTool())
        register(SystemKeyTool())
        register(OpenAppTool())
        register(GetInstalledAppsTool())
        register(TakeScreenshotTool())
        register(WaitTool())
        register(RepeatActionsTool())
        register(ClipboardTool())
        register(SendFileTool())
        register(FinishTool())
        register(CallUserTool())
    }

    private fun registerTvTools() {
        register(DpadUpTool())
        register(DpadDownTool())
        register(DpadLeftTool())
        register(DpadRightTool())
        register(DpadCenterTool())
        register(VolumeUpTool())
        register(VolumeDownTool())
        register(PressMenuTool())
        register(PressPowerTool())
    }

    private fun registerMobileTools() {
        register(TapTool())
        register(LongPressTool())
        register(SwipeTool())
        register(ScrollToFindTool())
    }

    /**
     * 注册 FPV 3D 世界建造工具
     * 这些工具通过 FPVToolBridge → WebView → Zustand Store 控制 3D 场景
     */
    fun registerFPVTools() {
        register(AddTreeTool())
        register(AddHouseBodyTool())
        register(AddRoofTool())
        register(AddWindowTool())
        register(AddGarageTool())
        register(AddRockTool())
        register(AddMountainTool())
        register(AddCloudTool())
        register(AddWaterTool())
        register(AddFloatingIslandTool())
        register(AddWallTool())
        register(AddRoadTool())
        register(AddLampTool())
        register(AddFenceTool())
        register(AddTowerTool())
        register(AddBridgeTool())
        register(AddFlowerTool())
        register(AddShrubTool())
        register(AddStatueTool())
        register(AddCampfireTool())
        register(AddSignTool())
        register(AddCrateTool())
        register(RemoveDynamicTool())
        register(ClearDynamicObjectsTool())
    }

    fun register(tool: BaseTool) {
        tools[tool.getName()] = tool
    }

    fun getTool(name: String): BaseTool? = tools[name]

    fun getDisplayName(name: String): String = tools[name]?.getDisplayName() ?: name

    fun getAllTools(): List<BaseTool> = tools.values.toList()

    fun executeTool(name: String, params: Map<String, Any>): ToolResult {
        val tool = tools[name] ?: return ToolResult.error("Unknown tool: $name")
        return try {
            tool.executeWithWaitAfter(params)
        } catch (e: Exception) {
            ToolResult.error("Tool execution failed: ${e.message}")
        }
    }
}
