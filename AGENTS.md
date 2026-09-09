# AGENTS.md

This file maps the repository guidance from `CLAUDE.md` into agent-agnostic instructions for coding assistants working in this project.

## 项目概述

**Squema Hook** 是一个 Xposed 模块（v1.9.0，versionCode 38），目标 5 个 Android App：

| 目标 | 包名 | hook 文件 |
|---|---|---|
| 番茄小说 | `com.dragon.read` | `HookInit.installHooks` (full 6-hook set) |
| 红果短剧 | `com.phoenix.read` | `HookInit.installHooks` (name-only 2-hook set) |
| 夸克浏览器 | `com.quark.browser` | `QuarkHookMain.installFromModern` |
| 小X分身 | `com.bly.dkplat` | `XiaoXVipHook.installFromModern` |
| Cherrygram | `uz.unnarsx.cherrygram` | `CherryGramVipHook.installFromModern` |

包名常量定义在 `Constants.java`（`PKG_FANQIE` / `PKG_HONGGUO` / `PKG_QUARK` / `PKG_XIAOX` / `PKG_CHERRYGRAM`）。

项目是**纯 Java + 单 app 模块**，基于 libxposed Modern Xposed API 102（`io.github.libxposed:api:102.0.0`，`compileOnly`）。

源码参考：`E:\New\squemaFQH\1\squemaFQH\` 是 v1.9.0 的同包源码备份（外部参考，不要和当前目录混淆），加进 `.gitignore`。

## 构建

- 构建 debug APK：`./gradlew assembleDebug`（输出在 `app/build/outputs/apk/debug/app-debug.apk`）
- Windows + Git Bash 必须先设环境变量：`export ANDROID_HOME="C:/Users/Squema-Mini/AppData/Local/Android/Sdk"`
- 清理：`./gradlew clean`
- 无测试套件，无 lint / 格式化配置。不要假设可以运行 `test` 或 `lint` 任务。

## 模块结构

```
app/src/main/
├── AndroidManifest.xml                 # xposed_description / QuarkSettingsActivity / QuarkConfigProvider
├── java/com/byterax/phoenix/read/
│   ├── HookApp.java                    # Application; XposedServiceHelper 注册 / StatusListener 广播
│   ├── HookInit.java                    # XposedModule 入口; onPackageLoaded/onPackageReady 分发
│   ├── Constants.java                   # PKG_FANQIE / HONGGUO / QUARK / XIAOX / CHERRYGRAM
│   ├── HookStatusFiles.java             # /data/local/tmp marker + isXxxHooked()
│   ├── HookStatusReporter.java          # AIDL 上报 (system_server → 模块 app)
│   ├── HookStatusStore.java             # SharedPreferences "hook_status_v1"
│   ├── ScopeStatus.java                 # OFF / SCOPED / LIVE 状态机
│   ├── LspScopeReader.java              # Magisk DB 兜底读 scope
│   ├── RuntimeDetector.java             # libxposed / Zygisk / root 检测
│   ├── MainActivity.java                # 黑金玻璃卡片 5 张; entrance animation; StatusListener
│   ├── service/                         # ServiceClient / ServiceProvider (AIDL)
│   ├── xposed/                          # SystemHookEntry / SystemBootstrap / SystemAmCompat / ...
│   ├── quark/                           # QuarkHookMain (~1360 行) / QuarkHooks / Config / Settings UI
│   ├── xiaox/                           # XiaoXVipHook (~950 行)
│   └── cherrygram/                      # CherryGramVipHook
└── resources/META-INF/xposed/
    ├── java_init.list                   # 仅 com.byterax.phoenix.read.HookInit (Modern API)
    ├── scope.list                       # android + 5 个目标包
    └── module.prop                      # minApiVersion=102 / targetApiVersion=102 / staticScope=true
```

## HookInit 分发契约（关键！）

`HookInit.java` 是**唯一的 Modern API 入口**。`scope.list` 里5 个目标包，每个包都必须在这里分发到对应 hook 实现：

| 回调 | 时机 | 用法 |
|---|---|---|
| `onModuleLoaded` | 模块首次注入进程 | `HookStatusFiles.markSystemReady()` |
| `onPackageLoaded` | 类加载完成（Application.attach 之前） | `QuarkHookMain.installFromModern(this, loader, "loaded")`（只在 `isFirstPackage()` 时） |
| `onPackageReady` | 应用 ClassLoader 完全 ready | 番茄 / 红果 / 夸克（ready） / 小X / Cherrygram |

**重要**：如果新增目标包，记得在 `Constants.java` 加常量 + `HookInit` 三大回调相应分支 + `scope.list` + `HookStatusFiles.isXxxHooked` + `MainActivity` 绑定。

## Xposed 模块约定

新增或修改 hook 时，下面四处必须保持一致，模块才会被 LSPosed 加载并作用于目标应用：

- **入口类**：`com.byterax.phoenix.read.HookInit`，继承 `io.github.libxposed.api.XposedModule`，并在 `app/src/main/resources/META-INF/xposed/java_init.list` 中注册（**仅此一行**，不要加 classic `IXposedHookLoadPackage` 实现 ——LSPosed 会因为找不到 `de.robv.android.xposed.IXposedHookLoadPackage` 接口而静默失败）
- **目标作用域**：`app/src/main/resources/META-INF/xposed/scope.list`（每行一个包名）
- **模块声明**：`app/src/main/resources/META-INF/xposed/module.prop`，当前声明 `minApiVersion=102` / `targetApiVersion=102` / `staticScope=true`
- **`Constants.PKG_*` 常量**：`app/src/main/java/com/byterax/phoenix/read/Constants.java`

## LSPosed 兼容性双构造器

`HookInit` 必须同时提供：

```java
public HookInit(XposedInterface base, XposedModuleInterface.ModuleLoadedParam param) { super(); }
public HookInit() { super(); }
```

- 第一种：LSPosed v2.2.0（Android 14+）反射查找的旧式 API 102 入口签名（v1.9.2 也用）
- 第二种：LSPosed 后续版本的纯无参入口（API 102 strict）

缺任何一种 LSPosed 会报 `NoSuchMethodException` 跳过模块加载。

## DexKit 依赖

本项目引入 `org.luckypray:dexkit:2.2.0`（`implementation`，LGPL-3.0）用于在番茄/红果短剧被混淆的 dex 里按特征签名找方法：

- 按方法名定位（`willShowLynxBanner` / `canThisPositionShow`）
- 按方法体内引用的字符串定位（`"followUserNum = %d..."` / `"doSyncInitUserInfo:%s"` / `"获取推荐用户数据成功"`）
- 按字段所属类定位（含 `CommentUserStrInfo` 字段的类）

**DexKit 是 `implementation`**：需把 `libdexkit.so`（arm64-v8a / armeabi-v7a / x86 / x86_64）打包进 APK，由目标进程运行时加载。**绝不能改成 `compileOnly`**。

换机器/重装时如下载失败，确认 `mavenCentral()`（或阿里云镜像）仓库可用。

## UI 同步

UI 黑金主题（glass card + 5 张状态卡片 + entrance animation + 模糊背景）资源在 `app/src/main/res/layout/`` + `res/values/{colors,strings,styles}.xml` + `res/drawable/bg_*.xml`。

**同步新 UI 时务必整盘替换**：不要只看 layout 而忘了 colors / strings / styles —— 用错资源名会让 `findViewById` 返回 null 静默降级到默认主题。

**关键 ID 名（v1.9.0 起生效）**：

- activity_main: `root_frame`, `atmosphere_layer`, `glass_blur_layer`, `vignette_layer`, `grain_layer`, `content_column`, `brand_title`, `brand_subtitle`, `card_fanqie`, `card_hongguo`, `card_quark`, `card_xiaox`, `hint_text`
- view_status_card: `status_card_root`, `status_card_orb`, `status_card_icon`, `status_card_title`, `status_card_subtitle`, `status_card_targets`, `status_card_chip`

## 关键陷阱

- **Xposed API 是 compile-only**：`compileOnly "io.github.libxposed:api:102.0.0"`，绝不能打包进 APK
- **classic API 不要已保留**：不要在 `java_init.list` 加 `IXposedHookLoadPackage` 实现（`SystemHookEntry` 只在 `android` 包触发 `SystemBootstrap`，不影响其他 hook），否则 Modern API 也会被牵连
- **AndroidX 已启用**：`gradle.properties` 中 `android.useAndroidX=true`，AGP 9.x 强制要求
- **工具链**：API 37 / AGP 9.2.0 / Gradle 9.5.1 / JDK 17 / buildTools 37.0.0；SDK platform 必须 `android-37.0` 新格式
- **Git Bash 环境变量**：Android SDK 不在 PATH，gradle 调用前必须 `export ANDROID_HOME="C:/Users/Squema-Mini/AppData/Local/Android/Sdk"`
- **macOS 元数据文件**：`.DS_Store` / `._*` 已加 `.gitignore`；提交前用 `find /e/New/squemaFQH -name "._*" -delete` 清一次
- **嵌入式 git 仓库**：`git add -A` 不会把外部 `1/squemaFQH/`（嵌套 git）加进主仓 index；提交前看 `git status --short` 别让嵌套 gitlink (`mode 160000`) 进 index
- **`SquemaQuark` / `SquemaFQHook` 日志**：这两 tag 空 = hook 没运行；**Quark / 小X / Cherrygram 是否生效看 SquemaQuark/SquemaFQHook/CherryGram 日志** —— 之前 1.9.0 不生效就是 HookInit 漏了 `onPackageLoaded` / 4 个 `PKG_*` 常量 / Quark/Xiaox 分发

## Agent 工作约定

- 优先保持项目现有结构：纯 Java、单 `app` 模块、Modern libxposed API 102。
- 修改 hook 逻辑时，保持目标包名过滤、入口注册、作用域和 `META-INF/xposed` 元数据一致。
- 不要引入不必要的 AndroidX、Kotlin、Compose、依赖管理或格式化工具。
- 验证构建时使用 `./gradlew assembleDebug`。不要运行不存在或未配置的测试、lint 任务。
- 文档或小范围配置变更无需构建；涉及 Java、Manifest、resources 或 Gradle 的改动应尽量跑一次 debug 构建。
- 提交前 `git status --short` 看清单，避免 `git add -A` 误带临时文件 / 嵌套 git。
- 同步新 UI / 新 hook 包时，先看 `1/squemaFQH` 源码备份，对照检查 HookInit.java、Constants.java、scope.list、META-INF/xposed、HookStatusFiles.java、MainActivity.java 6 个文件是否都已同步。