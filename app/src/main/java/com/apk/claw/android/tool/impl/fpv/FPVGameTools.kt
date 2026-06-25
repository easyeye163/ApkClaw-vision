package com.apk.claw.android.tool.impl.fpv

import com.apk.claw.android.tool.BaseTool
import com.apk.claw.android.tool.ToolParameter
import com.apk.claw.android.tool.ToolResult

/**
 * FPV 3D 世界建造工具 - 抽象基类
 *
 * 所有游戏建造工具（addTree, addHouseBody 等）继承此类，
 * 只需定义 name、description、parameters，执行逻辑统一通过 FPVToolBridge 转发到 WebView。
 */
abstract class FPVBuildTool : BaseTool() {

    /** 工具名称（如 "addTree"） */
    abstract fun toolName(): String

    /** 英文描述 */
    abstract fun toolDescEN(): String

    /** 中文描述 */
    abstract fun toolDescCN(): String

    /** 工具参数列表（不包含 wait_after，基类自动追加） */
    abstract fun toolParams(): List<ToolParameter>

    // === BaseTool implementations ===

    final override fun getName(): String = toolName()

    final override fun getDescriptionEN(): String = toolDescEN()

    final override fun getDescriptionCN(): String = toolDescCN()

    final override fun getParameters(): List<ToolParameter> = toolParams()

    final override fun execute(params: @JvmSuppressWildcards Map<String, Any>): ToolResult {
        val resultJson = FPVToolBridge.executeGameTool(toolName(), params)
        // Parse result
        return if (resultJson.contains("\"success\":true") || resultJson.contains("\"success\": true")) {
            ToolResult.success(resultJson)
        } else {
            ToolResult.error(resultJson)
        }
    }
}

// ======================== 22 个具体工具 ========================

// --- 树木 ---
class AddTreeTool : FPVBuildTool() {
    override fun toolName() = "addTree"
    override fun toolDescEN() = "Add a tree at the specified position. Composed of a cylindrical trunk and two conical canopies. Suitable for forests, parks, roadside planting."
    override fun toolDescCN() = "在指定位置添加一棵树。由圆柱树干+两层锥形树冠组成，适合森林、公园、路边绿化。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate (east-west direction)", true),
        ToolParameter("z", "number", "Z coordinate (north-south direction)", true),
        ToolParameter("height", "number", "Total tree height, default 3, range 1-8", false),
        ToolParameter("color", "string", "Canopy color hex. Green: #2d5a27 #1e7a1e #3a7d32, Autumn: #c97a2a #e8a030", false),
    )
    override fun getDisplayName() = "🌳 添加树木"
}

// --- 房屋主体 ---
class AddHouseBodyTool : FPVBuildTool() {
    override fun toolName() = "addHouseBody"
    override fun toolDescEN() = "Add a house body (walls) as a box. Use with addRoof to form a complete house."
    override fun toolDescCN() = "添加房屋主体（墙体），长方体。需要配合 addRoof 使用来组成完整房屋。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate (house center)", true),
        ToolParameter("z", "number", "Z coordinate (house center)", true),
        ToolParameter("width", "number", "Width (X direction), default 4", false),
        ToolParameter("height", "number", "Height, default 4", false),
        ToolParameter("depth", "number", "Depth (Z direction), default 4", false),
        ToolParameter("color", "string", "Wall color. Common: #d4a574(beige) #e8e0d0(white) #cc4444(red brick)", false),
    )
    override fun getDisplayName() = "🏠 添加房屋主体"
}

// --- 屋顶 ---
class AddRoofTool : FPVBuildTool() {
    override fun toolName() = "addRoof"
    override fun toolDescEN() = "Add a pyramid roof to place on top of a house body. Use with addHouseBody."
    override fun toolDescCN() = "添加四棱锥屋顶，放在房屋主体上方。配合 addHouseBody 使用。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate (align with house body)", true),
        ToolParameter("y", "number", "Y coordinate (usually = house height + half roof height)", true),
        ToolParameter("z", "number", "Z coordinate (align with house body)", true),
        ToolParameter("width", "number", "Bottom width, should >= house width, default 5", false),
        ToolParameter("height", "number", "Roof height, default 2", false),
        ToolParameter("depth", "number", "Bottom depth, default 5", false),
        ToolParameter("color", "string", "Roof color. Common: #c0392b(red) #8b4513(brown) #4a4a6a(dark blue-gray)", false),
    )
    override fun getDisplayName() = "🔺 添加屋顶"
}

// --- 窗户 ---
class AddWindowTool : FPVBuildTool() {
    override fun toolName() = "addWindow"
    override fun toolDescEN() = "Add a glowing window on a building wall. Should be placed on the wall surface."
    override fun toolDescCN() = "在建筑墙面添加发光窗户。需要贴在墙面上。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate", true),
        ToolParameter("y", "number", "Y coordinate (usually 1.5-4)", true),
        ToolParameter("z", "number", "Z coordinate (wall position)", true),
        ToolParameter("width", "number", "Window width, default 0.8", false),
        ToolParameter("height", "number", "Window height, default 1", false),
        ToolParameter("rotY", "number", "Y rotation radians. South=0, East=π/2, North=π, West=-π/2", false),
        ToolParameter("color", "string", "Window glow color, default #ffd700", false),
    )
    override fun getDisplayName() = "🪟 添加窗户"
}

// --- 车库 ---
class AddGarageTool : FPVBuildTool() {
    override fun toolName() = "addGarage"
    override fun toolDescEN() = "Add a garage (walls + rolling door + roof). Creates a complete garage in one step."
    override fun toolDescCN() = "添加车库（含墙体+卷帘门+屋顶），一步到位创建完整车库。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate", true),
        ToolParameter("z", "number", "Z coordinate", true),
        ToolParameter("width", "number", "Width, default 6", false),
        ToolParameter("height", "number", "Height, default 3.5", false),
        ToolParameter("depth", "number", "Depth, default 5", false),
        ToolParameter("color", "string", "Wall color, default #8a8a8a", false),
        ToolParameter("roofColor", "string", "Roof color, default #4a4a6a", false),
    )
    override fun getDisplayName() = "🚗 添加车库"
}

// --- 石头 ---
class AddRockTool : FPVBuildTool() {
    override fun toolName() = "addRock"
    override fun toolDescEN() = "Add a randomly shaped rock."
    override fun toolDescCN() = "添加一块随机形状的石头。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate", true),
        ToolParameter("z", "number", "Z coordinate", true),
        ToolParameter("scale", "number", "Size scale, default 0.8, range 0.2-3", false),
        ToolParameter("color", "string", "Color. Common: #7a7a7a(gray) #5a5050(dark gray) #a09080(sand brown)", false),
    )
    override fun getDisplayName() = "🪨 添加石头"
}

// --- 山脉 ---
class AddMountainTool : FPVBuildTool() {
    override fun toolName() = "addMountain"
    override fun toolDescEN() = "Add a low-poly mountain (cone + random displacement). Suitable for distant landscapes."
    override fun toolDescCN() = "添加一座低多边形山脉，锥形+随机扰动，适合远景和地形塑造。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate (mountain base center)", true),
        ToolParameter("z", "number", "Z coordinate (mountain base center)", true),
        ToolParameter("scaleX", "number", "X direction width, default 20", false),
        ToolParameter("scaleY", "number", "Height, default 25", false),
        ToolParameter("scaleZ", "number", "Z direction width, default 18", false),
        ToolParameter("color", "string", "Color. Common: #6b7b8d(blue-gray) #5c6d7e(dark gray) #8a7a6a(brown-gray)", false),
    )
    override fun getDisplayName() = "⛰️ 添加山脉"
}

// --- 云朵 ---
class AddCloudTool : FPVBuildTool() {
    override fun toolName() = "addCloud"
    override fun toolDescEN() = "Add a cloud composed of 3 spheres, floating in the air."
    override fun toolDescCN() = "添加一朵由3个球体组成的云，会浮在空中。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate", true),
        ToolParameter("z", "number", "Z coordinate", true),
        ToolParameter("y", "number", "Y coordinate (altitude), default 40, suggest 30-60", false),
        ToolParameter("scale", "number", "Cloud size, default 3, range 1-8", false),
    )
    override fun getDisplayName() = "☁️ 添加云朵"
}

// --- 水面 ---
class AddWaterTool : FPVBuildTool() {
    override fun toolName() = "addWater"
    override fun toolDescEN() = "Add a circular water surface with wave animation. Suitable for lakes and ponds."
    override fun toolDescCN() = "添加圆形水面（带波纹动画），适合湖泊、池塘。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate (water center)", true),
        ToolParameter("z", "number", "Z coordinate (water center)", true),
        ToolParameter("y", "number", "Y coordinate (water level), default -0.3", false),
        ToolParameter("radius", "number", "Water radius, default 15", false),
    )
    override fun getDisplayName() = "🌊 添加水面"
}

// --- 浮空岛 ---
class AddFloatingIslandTool : FPVBuildTool() {
    override fun toolName() = "addFloatingIsland"
    override fun toolDescEN() = "Add a floating island (cylinder base + grass + hover animation). For fantasy scenes."
    override fun toolDescCN() = "添加浮空岛（圆柱底座+草地+悬浮动画），适合奇幻场景。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate", true),
        ToolParameter("z", "number", "Z coordinate", true),
        ToolParameter("y", "number", "Y coordinate (hover altitude), default 15, suggest 10-30", false),
        ToolParameter("scale", "number", "Size scale, default 1", false),
    )
    override fun getDisplayName() = "🏝️ 添加浮空岛"
}

// --- 墙壁 ---
class AddWallTool : FPVBuildTool() {
    override fun toolName() = "addWall"
    override fun toolDescEN() = "Add a wall (thin box). Suitable for fences, fortifications, building exteriors."
    override fun toolDescCN() = "添加一面墙（薄长方体），适合围墙、壁垒、建筑外墙。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate (wall center)", true),
        ToolParameter("z", "number", "Z coordinate (wall center)", true),
        ToolParameter("length", "number", "Wall length, default 10", false),
        ToolParameter("height", "number", "Wall height, default 3", false),
        ToolParameter("color", "string", "Color, default #a09080", false),
    )
    override fun getDisplayName() = "🧱 添加墙壁"
}

// --- 道路 ---
class AddRoadTool : FPVBuildTool() {
    override fun toolName() = "addRoad"
    override fun toolDescEN() = "Add a road surface (dark gray plane). Suitable for roads and paths."
    override fun toolDescCN() = "添加路面（深灰色平面），适合道路、路径。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate (road center)", true),
        ToolParameter("z", "number", "Z coordinate (road center)", true),
        ToolParameter("length", "number", "Road length, default 20", false),
        ToolParameter("width", "number", "Road width, default 3", false),
        ToolParameter("direction", "string", "Direction: \"z\"(north-south) or \"x\"(east-west)", false),
    )
    override fun getDisplayName() = "🛤️ 添加道路"
}

// --- 路灯 ---
class AddLampTool : FPVBuildTool() {
    override fun toolName() = "addLamp"
    override fun toolDescEN() = "Add a street lamp (pole + glowing sphere + point light) that illuminates surroundings."
    override fun toolDescCN() = "添加路灯（柱体+发光球+点光源），可照亮周围环境。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate", true),
        ToolParameter("z", "number", "Z coordinate", true),
    )
    override fun getDisplayName() = "💡 添加路灯"
}

// --- 围栏 ---
class AddFenceTool : FPVBuildTool() {
    override fun toolName() = "addFence"
    override fun toolDescEN() = "Add a fence (evenly spaced posts + two horizontal rails). Suitable for yards, farmland borders."
    override fun toolDescCN() = "添加围栏（等距立柱+两道横栏），适合院子、农田边界。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate (fence center)", true),
        ToolParameter("z", "number", "Z coordinate (fence center)", true),
        ToolParameter("length", "number", "Fence length, default 10", false),
        ToolParameter("height", "number", "Fence height, default 1.2", false),
        ToolParameter("color", "string", "Color, default #8B7355", false),
        ToolParameter("direction", "string", "Direction: \"z\"(north-south) or \"x\"(east-west)", false),
    )
    override fun getDisplayName() = "🏗️ 添加围栏"
}

// --- 瞭望塔 ---
class AddTowerTool : FPVBuildTool() {
    override fun toolName() = "addTower"
    override fun toolDescEN() = "Add a watchtower (cylinder body + conical top + 4 windows). Suitable for castles, guard towers."
    override fun toolDescCN() = "添加瞭望塔（圆柱塔身+锥形塔顶+四面窗户），适合城堡、哨塔。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate", true),
        ToolParameter("z", "number", "Z coordinate", true),
        ToolParameter("height", "number", "Tower body height, default 12", false),
        ToolParameter("radius", "number", "Tower base radius, default 2", false),
        ToolParameter("color", "string", "Tower body color, default #8a8070", false),
        ToolParameter("roofColor", "string", "Tower top color, default #6a3a2a", false),
    )
    override fun getDisplayName() = "🏰 添加瞭望塔"
}

// --- 桥梁 ---
class AddBridgeTool : FPVBuildTool() {
    override fun toolName() = "addBridge"
    override fun toolDescEN() = "Add a bridge (deck + side railings + support pillars). Suitable for crossing water or trenches."
    override fun toolDescCN() = "添加桥梁（桥面+两侧护栏+支撑柱），适合跨越水面或沟壑。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate (bridge center)", true),
        ToolParameter("z", "number", "Z coordinate (bridge center)", true),
        ToolParameter("length", "number", "Bridge length, default 12", false),
        ToolParameter("width", "number", "Bridge width, default 3", false),
        ToolParameter("y", "number", "Bridge deck height, default 1", false),
        ToolParameter("direction", "string", "Direction: \"z\"(north-south) or \"x\"(east-west)", false),
        ToolParameter("color", "string", "Deck color, default #8B7355", false),
    )
    override fun getDisplayName() = "🌉 添加桥梁"
}

// --- 花朵 ---
class AddFlowerTool : FPVBuildTool() {
    override fun toolName() = "addFlower"
    override fun toolDescEN() = "Add a flower (thin stem + spherical head). Suitable for gardens, lawn decoration."
    override fun toolDescCN() = "添加一朵花（细茎+球形花头），适合花园、草地装饰。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate", true),
        ToolParameter("z", "number", "Z coordinate", true),
        ToolParameter("color", "string", "Flower color. Common: #ff6b9d(pink) #ff4444(red) #ffdd00(yellow) #aa44ff(purple)", false),
    )
    override fun getDisplayName() = "🌸 添加花朵"
}

// --- 灌木 ---
class AddShrubTool : FPVBuildTool() {
    override fun toolName() = "addShrub"
    override fun toolDescEN() = "Add a shrub (two-sphere combination). Shorter than trees, suitable for garden decoration."
    override fun toolDescCN() = "添加灌木丛（两个球体组合），比树矮小，适合灌木丛、花园装饰。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate", true),
        ToolParameter("z", "number", "Z coordinate", true),
        ToolParameter("scale", "number", "Size, default 0.8", false),
        ToolParameter("color", "string", "Color, default #3a7d32", false),
    )
    override fun getDisplayName() = "🌿 添加灌木"
}

// --- 雕像 ---
class AddStatueTool : FPVBuildTool() {
    override fun toolName() = "addStatue"
    override fun toolDescEN() = "Add a statue (square base + cylindrical body + spherical head). Suitable for squares, garden centerpieces."
    override fun toolDescCN() = "添加雕像（方形底座+圆柱体身+球形头），适合广场、花园中心装饰。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate", true),
        ToolParameter("z", "number", "Z coordinate", true),
        ToolParameter("height", "number", "Statue body height, default 3", false),
        ToolParameter("color", "string", "Statue color, default #c0b0a0", false),
    )
    override fun getDisplayName() = "🗽 添加雕像"
}

// --- 篝火 ---
class AddCampfireTool : FPVBuildTool() {
    override fun toolName() = "addCampfire"
    override fun toolDescEN() = "Add a campfire (stone ring + firewood + animated flames + point light). Suitable for campsites."
    override fun toolDescCN() = "添加篝火（石头围圈+木柴+动态火焰+点光源），适合营地、野外场景。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate", true),
        ToolParameter("z", "number", "Z coordinate", true),
    )
    override fun getDisplayName() = "🔥 添加篝火"
}

// --- 指示牌 ---
class AddSignTool : FPVBuildTool() {
    override fun toolName() = "addSign"
    override fun toolDescEN() = "Add a signpost (wooden post + square sign board). Suitable for road signs, area markers."
    override fun toolDescCN() = "添加指示牌（木柱+方形牌面），适合路标、区域标记。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate", true),
        ToolParameter("z", "number", "Z coordinate", true),
        ToolParameter("text", "string", "Sign text (record only, not rendered in 3D)", false),
    )
    override fun getDisplayName() = "🪧 添加指示牌"
}

// --- 木箱 ---
class AddCrateTool : FPVBuildTool() {
    override fun toolName() = "addCrate"
    override fun toolDescEN() = "Add a wooden crate (box + cross metal bands). Suitable for warehouses, docks, cargo."
    override fun toolDescCN() = "添加木箱（方块+十字金属带），适合仓库、码头、货物堆放。"
    override fun toolParams() = listOf(
        ToolParameter("x", "number", "X coordinate", true),
        ToolParameter("z", "number", "Z coordinate", true),
        ToolParameter("size", "number", "Crate size, default 1", false),
        ToolParameter("color", "string", "Crate color, default #8B6914", false),
    )
    override fun getDisplayName() = "📦 添加木箱"
}

// --- 删除动态物体 ---
class RemoveDynamicTool : FPVBuildTool() {
    override fun toolName() = "removeDynamic"
    override fun toolDescEN() = "Remove a previously added dynamic object by its ID."
    override fun toolDescCN() = "删除一个已添加的动态物体（通过其 ID）。"
    override fun toolParams() = listOf(
        ToolParameter("id", "string", "The object ID to remove (returned in addXxx results)", true),
    )
    override fun getDisplayName() = "🗑️ 删除物体"
}

// --- 清除所有动态物体 ---
class ClearDynamicObjectsTool : FPVBuildTool() {
    override fun toolName() = "clearDynamicObjects"
    override fun toolDescEN() = "Remove all dynamically added objects, restoring the world to its initial state."
    override fun toolDescCN() = "清除所有通过工具添加的动态物体，恢复到初始世界状态。"
    override fun toolParams(): List<ToolParameter> = emptyList()
    override fun getDisplayName() = "🧹 清除所有物体"
}