English | [简体中文](./README-zh.md)

<img src="screenshots/opencode-wordmark-light.png"/>

# OpenCode Android Client

OpenCode Android Client is a Jetpack Compose-based mobile sample for OpenCode.
It connects to an OpenCode Server on your local network, lets you select projects, and chat in sessions.

## Quick Start

1. Open the project with the latest stable Android Studio.
2. Start OpenCode Server on your dev machine (example):
   - `opencode serve --hostname 0.0.0.0 --port 4096 --mdns`
3. Ensure your phone and server are on the same LAN. The app will discover the server via mDNS.

## Core Capabilities

- mDNS discovery for `_opencode._tcp.` services
- OpenCode session and project selection
- Build / Plan mode switching
- Model and reasoning-level selection
- Streaming message rendering and state management
- Material 3 theme with OpenCode design tokens

## Main Modules

- `opencode/discovery`: service discovery wrapper (`NsdManager`)
- `opencode/api`: OpenCode HTTP API client and JSON parsing
- `opencode/model`: client-side models for service/session/project/message
- `opencode/OpencodeViewModel`: orchestration for discovery, connection, sessions, and messaging
- `conversation/OpenCodeConversation.kt`: OpenCode chat UI

## Screenshots

<p align="left">
  <img src="screenshots/01_服务器设置_preview.png" width="220"/>
  <img src="screenshots/02_会话记录_preview.png" width="220"/>
  <img src="screenshots/03_模型选择_preview.png" width="220"/>
  <img src="screenshots/04_消息1_preview.png" width="220"/>
  <img src="screenshots/05_消息2_preview.png" width="220"/>
  <img src="screenshots/06_消息3_preview.png" width="220"/>
  <img src="screenshots/07_主页_preview.png" width="220"/>
</p>

## Testing

- Unit tests: `./gradlew :app:testDebugUnitTest`
- UI tests: `./gradlew :app:connectedDebugAndroidTest`

Key test directories:
- `app/src/test/java/com/github/go_kenka/opencode/opencode`
- `app/src/androidTest/java/com/github/go_kenka/opencode`

## Status

This project is still under active development, and some features may not be fully implemented yet.

## License
```
Copyright 2020 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
