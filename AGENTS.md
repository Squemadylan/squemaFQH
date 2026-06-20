# AGENTS.md

This file maps the repository guidance from `CLAUDE.md` into agent-agnostic instructions for coding assistants working in this project.

## 项目概述

FanQieHook 是一个 Xposed 模块，用于 hook 番茄小说（包名 `com.dragon.read`）的 VIP 状态。项目是纯 Java、单模块，基于 libxposed Modern Xposed API 102（`io.github.libxposed:api:102.0.0`）。

## 构建

- 构建 debug APK：`./gradlew assembleDebug`（输出在 `app/build/outputs/apk/debug/`）
- 清理：`./gradlew clean`
- 无测试套件，无 lint / 格式化配置。不要假设可以运行 `test` 或 `lint` 任务。

## Xposed 模块约定

新增或修改 hook 时，以下三处必须保持一致，模块才会被支持 Modern Xposed API 的框架加载并作用于目标应用：

- **入口类**：`com.byterax.dragon.read.HookInit`，继承 `io.github.libxposed.api.XposedModule`，并在 `app/src/main/resources/META-INF/xposed/java_init.list` 中注册（一行一个全限定类名）
- **目标作用域**：`app/src/main/resources/META-INF/xposed/scope.list`（当前为 `com.dragon.read`）
- **模块声明**：`app/src/main/resources/META-INF/xposed/module.prop`，当前声明 `minApiVersion=102` / `targetApiVersion=102` / `staticScope=true`

新增 hook 点时，在 `HookInit.onPackageReady` 里先按 `param.getPackageName()` 判断目标包名，再执行 hook。可参考现有的 `VipInfoModel` 构造函数 hook。

### DexKit 依赖（用于还原 hook ①③④⑤⑥）

本项目引入 `org.luckypray:dexkit`（当前 `2.0.7`，`implementation`，LGPL-3.0）用于在番茄小说被混淆的 dex 里按**特征签名**定位目标方法，对应还原报告（`Anti/HookVip/com.dragon.read-hook-analysis.md`，即 HookVip **v4.1.6** 中 `C1078.mo656()` 的注册流程，回调分发器 `C1992` 的 case 12–16）中的 hook ①③④⑤⑥，实现"全版本通杀"。v4.1.6 的 DexKit 查询经 AES-CBC+XOR 解密后，与本模块使用的签名逐条核对一致：

- 按方法名定位（`willShowLynxBanner` / `canThisPositionShow`）—— 类名混淆但方法名保留
- 按方法体内引用的字符串定位（`"followUserNum = %d..."` / `"doSyncInitUserInfo:%s"` / `"获取推荐用户数据成功"`）
- 按字段所属类定位（含 `com.dragon.read.rpc.model.CommentUserStrInfo` 字段的类）

与 libxposed 的 `compileOnly` 不同，**DexKit 是 `implementation`**：需把 `libdexkit.so`（arm64-v8a / armeabi-v7a / x86 / x86_64）打包进 APK，由目标进程在运行时加载。切勿把 DexKit 改成 `compileOnly`。换机器/重装时如下载失败，确认 `mavenCentral()`（或镜像）仓库可用。

## 关键陷阱

- **Xposed API 是 compile-only**：依赖使用 `compileOnly "io.github.libxposed:api:102.0.0"`，绝不能打包进 APK，否则会与宿主框架冲突。不要把它从 `compileOnly` 改成 `implementation`。
- **API 102 不兼容旧 API 调用**：target API 102 后不能调用 `de.robv.android.xposed.*` 旧 API；hook 入口和回调必须使用 `io.github.libxposed.api.*`。
- **AndroidX 已启用**：`gradle.properties` 中 `android.useAndroidX=true`。AGP 9.x 强制要求该值为 true。代码本身是纯 Java + Xposed，未引入 AndroidX 依赖；`android.enableJetifier=false` 保持不变（无 support 依赖，无需 jetify）。
- **工具链已升级到 API 37（2026-06-20）**：`compileSdkVersion 37` / `targetSdkVersion 37` / `minSdkVersion 26`，配 AGP 9.2.0 + Gradle 9.5.1 + JDK 17 + buildTools 37.0.0。关键坑：本机 SDK platform 装成 `android-37.0`（`AndroidVersion.ApiLevel=37.0` 新格式），AGP 8.x 不认这种命名、会报 `Failed to find Platform SDK platforms;android-37`；必须 AGP 9.x 才能识别。换机器重装 SDK 若再现，升 AGP 9.x 即可。
- **非 git 仓库**：本项目未纳入版本控制，没有分支 / PR 约定。改动前后需要自行留意文件差异或手动备份。

## Agent 工作约定

- 优先保持项目现有结构：纯 Java、单 `app` 模块、Modern Xposed API 102。
- 修改 hook 逻辑时，保持目标包名过滤、入口注册、作用域和 `META-INF/xposed` 元数据一致。
- 不要引入不必要的 AndroidX、Kotlin、Compose、依赖管理或格式化工具。
- 验证构建时使用 `./gradlew assembleDebug`。不要运行不存在或未配置的测试、lint 任务。
- 文档或小范围配置变更无需构建；涉及 Java、Manifest、resources 或 Gradle 的改动应尽量跑一次 debug 构建。
