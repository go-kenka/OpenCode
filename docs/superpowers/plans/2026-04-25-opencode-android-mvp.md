# OpenCode Android MVP 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在现有 Android Jetchat 示例中实现 OpenCode 手机端 MVP：mDNS 自动发现、顶部项目选择、暗色聊天输入界面和 Build/Plan/模型/思考等级选择。

**架构：** 新增 `opencode` 包封装发现、API、模型和 ViewModel；改造 `conversation` 页面渲染 OpenCode UI。所有网络和发现逻辑离开 Composable，UI 只消费 `StateFlow` 状态和事件回调。

**技术栈：** Kotlin、Jetpack Compose、Android ViewModel、Kotlin Coroutines、Android NsdManager、OkHttp、org.json、JUnit4。

---

## 文件结构

- 创建：`app/src/main/java/com/example/compose/jetchat/opencode/model/OpenCodeModels.kt`，客户端模型、UI 状态、选择项。
- 创建：`app/src/main/java/com/example/compose/jetchat/opencode/api/OpenCodeJson.kt`，JSON 解析和请求构建纯函数。
- 创建：`app/src/main/java/com/example/compose/jetchat/opencode/api/OpenCodeClient.kt`，OkHttp API client。
- 创建：`app/src/main/java/com/example/compose/jetchat/opencode/discovery/OpenCodeDiscovery.kt`，mDNS discovery 接口与 NsdManager 实现。
- 创建：`app/src/main/java/com/example/compose/jetchat/opencode/OpencodeViewModel.kt`，状态机和业务编排。
- 创建：`app/src/main/java/com/example/compose/jetchat/conversation/OpenCodeConversation.kt`，OpenCode 风格 Compose UI。
- 修改：`app/src/main/java/com/example/compose/jetchat/conversation/ConversationFragment.kt`，接入新 ViewModel/UI。
- 修改：`app/src/main/AndroidManifest.xml`，增加网络和 mDNS 所需权限，允许 HTTP cleartext。
- 修改：`app/build.gradle.kts`，增加 OkHttp 和本地单元测试依赖。
- 创建：`app/src/test/java/com/example/compose/jetchat/opencode/api/OpenCodeJsonTest.kt`，JSON 行为测试。
- 创建：`app/src/test/java/com/example/compose/jetchat/opencode/model/OpenCodeModelsTest.kt`，项目聚合和 URL 行为测试。

## 任务 1：模型和 JSON 纯函数

**文件：**
- 创建：`app/src/main/java/com/example/compose/jetchat/opencode/model/OpenCodeModels.kt`
- 创建：`app/src/main/java/com/example/compose/jetchat/opencode/api/OpenCodeJson.kt`
- 测试：`app/src/test/java/com/example/compose/jetchat/opencode/model/OpenCodeModelsTest.kt`
- 测试：`app/src/test/java/com/example/compose/jetchat/opencode/api/OpenCodeJsonTest.kt`

- [ ] **步骤 1：编写失败测试**

测试应覆盖：`OpenCodeService.baseUrl` 使用 resolved host/port；session 列表聚合为项目；message response 中只拼接 `type=text` 的 parts；发送请求 JSON 包含 `agent` 和 `parts`，模型为空时不写 `model`。

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew testDebugUnitTest --tests '*OpenCode*'`
预期：编译失败或测试失败，因为生产类型尚不存在。

- [ ] **步骤 3：实现最少生产代码**

定义 `OpenCodeService`、`OpenCodeProject`、`OpenCodeChatMessage`、`OpenCodeMode`、`OpenCodeModelOption`、`ThinkingLevel`、`OpenCodeUiState` 和 `OpenCodeJson` helper。

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew testDebugUnitTest --tests '*OpenCode*'`
预期：新增纯函数测试通过。

## 任务 2：mDNS discovery 和 HTTP client

**文件：**
- 创建：`app/src/main/java/com/example/compose/jetchat/opencode/discovery/OpenCodeDiscovery.kt`
- 创建：`app/src/main/java/com/example/compose/jetchat/opencode/api/OpenCodeClient.kt`
- 修改：`app/src/main/AndroidManifest.xml`
- 修改：`app/build.gradle.kts`

- [ ] **步骤 1：添加依赖和权限**

添加 `implementation(libs.okhttp3)`，Manifest 增加 `INTERNET`、`ACCESS_NETWORK_STATE`、`CHANGE_WIFI_MULTICAST_STATE`，并为 MVP 允许 cleartext HTTP。

- [ ] **步骤 2：实现 discovery**

`NsdOpenCodeDiscovery` 使用 `NsdManager.discoverServices("_opencode._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)`，解析服务后发出 `OpenCodeService`。

- [ ] **步骤 3：实现 client**

`OpenCodeClient` 提供 `health(baseUrl)`、`listSessions(baseUrl)`、`createSession(baseUrl)`、`sendMessage(baseUrl, sessionId, prompt, mode, model, thinkingLevel)`。

- [ ] **步骤 4：运行构建检查**

运行：`./gradlew testDebugUnitTest --tests '*OpenCode*'`
预期：测试通过且 main 编译无类型错误。

## 任务 3：ViewModel 状态机

**文件：**
- 创建：`app/src/main/java/com/example/compose/jetchat/opencode/OpencodeViewModel.kt`

- [ ] **步骤 1：实现状态机**

ViewModel 启动 discovery；发现服务后连接；从 sessions 聚合项目；选择项目；发送消息时先追加用户消息，再追加助手或错误消息。

- [ ] **步骤 2：暴露 UI 事件**

公开 `selectProject(projectId)`、`selectMode(mode)`、`selectModel(model)`、`selectThinking(level)`、`sendMessage(text)`、`retryDiscovery()`。

- [ ] **步骤 3：运行编译检查**

运行：`./gradlew testDebugUnitTest --tests '*OpenCode*'`
预期：测试通过且 ViewModel 编译通过。

## 任务 4：Compose UI 改造

**文件：**
- 创建：`app/src/main/java/com/example/compose/jetchat/conversation/OpenCodeConversation.kt`
- 修改：`app/src/main/java/com/example/compose/jetchat/conversation/ConversationFragment.kt`

- [ ] **步骤 1：实现暗色页面**

实现顶部项目选择、空状态、消息列表和底部输入区。底部必须包含 Build/Plan、模型和思考等级选择。

- [ ] **步骤 2：接入 Fragment**

`ConversationFragment` 使用 `by viewModels<OpencodeViewModel>()`，`collectAsStateWithLifecycle()` 后渲染 `OpenCodeConversationScreen`。

- [ ] **步骤 3：运行编译检查**

运行：`./gradlew testDebugUnitTest --tests '*OpenCode*'`
预期：测试通过且 UI 编译通过。

## 任务 5：整体验证

**文件：**
- 检查所有新增和修改文件

- [ ] **步骤 1：运行单元测试**

运行：`./gradlew testDebugUnitTest --tests '*OpenCode*'`
预期：OpenCode 相关测试通过。

- [ ] **步骤 2：运行 Debug 构建**

运行：`./gradlew assembleDebug`
预期：Debug APK 构建成功。

- [ ] **步骤 3：需求核对**

核对：无手动地址输入；mDNS 扫描 `_opencode._tcp.`；顶部可选择项目；聊天 UI 有 Build/Plan、模型、思考等级。
