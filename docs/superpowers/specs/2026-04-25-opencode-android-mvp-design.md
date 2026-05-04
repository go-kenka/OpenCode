# OpenCode Android MVP 设计

## 目标

在现有 OpenCode Android 示例项目上实现一个手机端 OpenCode MVP：自动发现局域网 OpenCode server，选择项目，并在暗色聊天界面中发送消息，支持 Build/Plan 模式、模型选择和思考等级选择。

## 范围

本版本只支持 mDNS 自动发现，不提供手动 server 地址输入。发现不到服务时显示启动命令提示：`opencode serve --hostname 0.0.0.0 --port 4096 --mdns`。

聊天只做当前内存会话，不做账号、持久化、文件上传、SSE 流式渲染、Basic Auth 和完整多会话管理。

## 架构

- `opencode/discovery`：封装 Android `NsdManager`，扫描 `_opencode._tcp.` 服务并解析为 `OpenCodeService`。
- `opencode/api`：轻量 HTTP client，使用 OkHttp 和 `org.json` 调用 health、session、message API。
- `opencode/model`：定义项目、模型、消息、模式和思考等级等客户端模型。
- `opencode/OpencodeViewModel`：协调自动发现、项目选择、会话创建、消息发送和 UI 状态。
- `conversation` UI：保留 Fragment 入口，但替换为 OpenCode 风格聊天页面和底部输入面板。

## 数据流

1. `ConversationFragment` 创建 `OpencodeViewModel`。
2. ViewModel 启动 mDNS 发现。
3. 发现服务后调用 `/global/health` 和 `/session`。
4. ViewModel 将 session 聚合为项目列表，默认选中第一个；没有 session 时创建一个默认 session。
5. 用户选择 Build/Plan、模型、思考等级并发送文本。
6. ViewModel 调用 `/session/{id}/message`，把返回的 text parts 合并为助手消息。

## UI

整体使用暗色视觉，不沿用 OpenCode 社交抽屉风格作为主要体验。顶部显示当前项目名称、路径和服务状态，点击可选择项目。空状态居中显示项目图标、文案、路径、分支占位和最后修改占位。底部输入区接近用户截图：大圆角输入框、左侧加号、右侧发送按钮，下方选择 Build/Plan、模型和思考等级。

## 错误处理

- 未发现服务：显示启动命令和扫描状态。
- 健康检查失败：显示服务不可用错误，继续发现。
- 发送失败：保留用户消息并追加错误提示消息。
- 模型接口不稳定：MVP 使用默认模型列表，发送时可省略模型或发送已知 provider/model。

## 测试

新增本地单元测试覆盖纯 Kotlin 行为：服务 URL 构建、session 到项目聚合、消息 part 文本提取、发送请求 JSON 构建。Android `NsdManager` 本身用接口隔离，不在本地单元测试直接实例化。

## 限制

真实 mDNS 发现依赖 OpenCode server 端广播 `_opencode._tcp.`。如果 server 未广播，Android 客户端无法自动发现服务。本版本按用户要求不增加手动地址兜底。
