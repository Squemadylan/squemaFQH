# SquemaFQHook

> 基于 [Modern libxposed API 102](https://github.com/libxposed/api) 的 Xposed 模块，同时解锁番茄小说（`com.dragon.read`）与红果短剧（`com.phoenix.read`）的 VIP 状态。

项目名取自 **番茄 + 红果** 的拼音首字母（Squema = 两个拼音的合写）。

## 项目概述

**SquemaFQHook** 是一个纯 Java 的单模块 Android Xposed 模块，在目标进程内通过反射 + DexKit 特征签名定位混淆方法并挂钩，实现：

- **番茄小说**（`com.dragon.read`）：VIP 解锁、关 Lynx 横幅广告、屏蔽个人页推广广告位、伪造关注/粉丝/获赞、伪造昵称、伪造头像、清空推荐用户
- **红果短剧**（`com.phoenix.read`）：VIP 解锁、伪造昵称（红果内部复用 `com.dragon.read` 的 `VipInfoModel` 类，仅需这两条 hook）

### 几个有趣的发现

1. **红果短剧内部直接复用 `com.dragon.read` 的核心 VIP 模型** —— 通过 `dexdump classes13.dex` 验证：`com.dragon.read.user.model.VipInfoModel` 构造函数签名完全一致 `(String,String,String,boolean,boolean,int,boolean,VipCommonSubType)`。所以同一段 hook 代码可以同时作用于两个 app。
2. **本项目从 `ByteRax/FanQieHook` fork 而来**，核心 hook 逻辑与 `HookVip v4.1.6` 中 `C1078.mo656()` 的注册流程一一对应，通过解密 AES-CBC+XOR 字符串常量逐条核对签名，实现"全版本通杀"。
3. **昵称定制为 `调教司`**（按用户要求），未挂钩头像。

## 运行环境

| 项 | 要求 |
| --- | --- |
| Android | 8.0+ (API 26) |
| 编译/目标 SDK | Android 14 (API 37) |
| 宿主框架 | LSPosed / 其他支持 libxposed API 102 的现代 Xposed 框架 |
| 目标应用 | 番茄小说 `com.dragon.read`、红果短剧 `com.phoenix.read` |

## 技术栈

| 项 | 版本 |
| --- | --- |
| Android Gradle Plugin | 9.2.0 |
| Gradle | 9.5.1 |
| JDK | 17 |
| Android Build Tools | 37.0.0 |
| 语言 | 纯 Java（无 Kotlin，无 AndroidX 依赖） |

## 模块状态显示

模块 app 顶部状态卡片：

- **绿色**：检测到 libxposed / Zygisk / root（任一）→ Hook 通道已连接
- **灰色**：未检测到任何宿主框架环境

副标题会显示具体检测结果，例如：

```
libxposed:false / Zygisk:false / root:true
```

目标行固定显示：

```
番茄 ✓ / 红果 ✓
```

> 卡片颜色只是 UI 显示，不影响 Hook 是否生效。真正确认 Hook 跑起来的方式是：**首次打开番茄或红果时屏幕弹出 Toast `番茄红果 VIP Hook 成功`**。

## 关键依赖

```gradle
// libxposed API —— compileOnly: 不打包进 APK，避免与宿主框架冲突
compileOnly "io.github.libxposed:api:102.0.0"

// DexKit —— implementation: 用于在混淆 dex 中按特征签名定位目标方法
// 必须把 libdexkit.so (arm64-v8a / armeabi-v7a / x86 / x86_64) 打包进 APK
implementation "org.luckypray:dexkit:2.2.0"
```

> **警告**：切勿将 libxposed 改为 `implementation`，否则会与宿主框架冲突。DexKit 必须是 `implementation`，否则 native 库无法加载。

## 构建

```bash
# 在 Windows 上使用 gradlew.bat
.\gradlew.bat assembleDebug --no-daemon
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 安装

```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

## 使用

1. 在 LSPosed（或兼容框架）中启用本模块
2. 作用域勾选：**番茄小说** `com.dragon.read`、**红果短剧** `com.phoenix.read`
3. 重启手机生效
4. 首次打开番茄小说或红果短剧，应弹出 Toast `番茄红果 VIP Hook 成功`
5. 会员图标、到期时间、昵称（番茄）、关注/粉丝/获赞等已替换

## 项目结构

```
app/src/main/
├── AndroidManifest.xml
├── aidl/com/byterax/phoenix/read/IHookStatusService.aidl
├── assets/xposed_init                                # classic Xposed 入口（legacy）
├── java/com/byterax/phoenix/read/
│   ├── HookInit.java                  # 现代 libxposed 入口（处理 target App）
│   ├── HookApp.java
│   ├── MainActivity.java              # 状态卡片 UI
│   ├── RuntimeDetector.java           # 简化版框架检测（libxposed/Zygisk/root）
│   ├── Constants.java
│   ├── service/
│   │   ├── ServiceClient.java         # 模块 App 端 AIDL 客户端
│   │   └── ServiceProvider.java       # 模块 App 端 ContentProvider（接收 system 推 Binder）
│   ├── xposed/
│   │   ├── SystemHookEntry.java       # classic IXposedHookLoadPackage 入口
│   │   ├── SystemBootstrap.java       # system_server 内启动 AIDL Stub
│   │   ├── SystemAmCompat.java        # hidden API（ActivityManager / IPackageManager）
│   │   ├── SystemUserService.java     # attachApplicationLocked hook，推送 Binder
│   │   ├── HookStatusService.java     # AIDL Stub
│   │   └── HookStatusReporter.java    # 目标进程上报 Hook 状态
│   └── HookStatusFiles.java           # 文件通道回退
├── res/
│   ├── layout/
│   │   ├── activity_main.xml
│   │   └── view_status_card.xml
│   ├── drawable/status_card_bg.xml
│   ├── values/{colors,strings}.xml
│   └── values-zh/strings.xml
└── resources/META-INF/xposed/
    ├── java_init.list                 # 现代入口类注册
    ├── module.prop                    # 模块元数据
    └── scope.list                     # 作用域列表
```

## Hook 列表（番茄小说）

| # | 功能 | 签名特征 | 替换策略 |
|---|------|---------|---------|
| ① | 关闭 Lynx 横幅广告 | 方法名 `willShowLynxBanner` | 返回 `false` |
| ② | 解锁会员 | `VipInfoModel` 全部构造函数 | 篡改 `expireTime/isVip/leftTime` |
| ③ | 屏蔽个人页推广广告位 | 方法名 `canThisPositionShow` | 拉满 `leftTime`，清空 `text` |
| ④ | 伪造关注/粉丝/获赞 | 引用 `"followUserNum = %d, fansNum = %d, ..."` | 改 `followUserNum=5200000` 等 |
| ⑤a | 伪造昵称（同步入口） | 引用 `"doSyncInitUserInfo:%s"` | 改 `userName="调教司"` |
| ⑤b | 伪造昵称（评论用户） | 持有 `CommentUserStrInfo` 字段的类 | 改 `userName="调教司"` |
| ⑥ | 清空推荐用户 | 引用 `"获取推荐用户数据成功"` | `args[0] = null` |

每条 hook 都用独立的 `safeHook(...)` 包裹，一条失败不影响其余。所有 hook 使用 `PROTECTIVE` 异常模式。

## Hook 列表（红果短剧）

- ② 解锁会员（与番茄同源）
- ⑤ 伪造昵称为 `调教司`

## Xposed 模块约定

| 元数据 | 位置 |
| --- | --- |
| **入口类** | `com.byterax.phoenix.read.HookInit`（`io.github.libxposed.api.XposedModule`）+ `com.byterax.phoenix.read.xposed.SystemHookEntry`（classic `IXposedHookLoadPackage`），在 `app/src/main/resources/META-INF/xposed/java_init.list` 注册 |
| **目标作用域** | `app/src/main/resources/META-INF/xposed/scope.list`（`com.dragon.read`、`com.phoenix.read`） |
| **模块声明** | `app/src/main/resources/META-INF/xposed/module.prop`（`minApiVersion=102`、`targetApiVersion=102`、`staticScope=true`） |

新增 hook 时，在 `HookInit.onPackageReady` 里按 `param.getPackageName()` 分发到对应包即可。

## DexKit 加载说明

DexKit 2.x 不会自动加载 `libdexkit.so`，本模块在 `HookInit.createDexKitBridge` 中做了两层兜底：

1. `System.loadLibrary("dexkit")`：多数框架（如 LSPosed）会把模块 APK 的 lib 加入目标进程库搜索路径；
2. 失败回退：取模块 APK 的 `nativeLibraryDir` 用绝对路径 `System.load(...)`。

如果两种方式都失败，会在 logcat 中出现 `UnsatisfiedLinkError: Could not load libdexkit.so`。

## 关键陷阱

- **libxposed 是 `compileOnly`**：绝不能改为 `implementation`
- **API 102 不兼容旧 API**：不能调用 `de.robv.android.xposed.*`，必须用 `io.github.libxposed.api.*`
- **DexKit 是 `implementation`**：必须把 `libdexkit.so` 打包进 APK，切勿改为 `compileOnly`
- **AndroidX 已启用**：`android.useAndroidX=true`（AGP 9.x 强制要求），`enableJetifier=false`（无 support 依赖）
- **SDK 平台命名**：本机 SDK 装成 `android-37.0`（`AndroidVersion.ApiLevel=37.0` 新格式），AGP 8.x 不识别，必须使用 AGP 9.x

## 调试

- 模块加载日志 TAG：`SquemaFQHook`，可用 `adb logcat -s SquemaFQHook` 查看
- Hook 失败时会记录 `Log.ERROR` 并附带堆栈

## 许可证

本项目使用 GPL-3.0 许可证（详见 `LICENSE` 文件）。所依赖的 DexKit（`org.luckypray:dexkit`）为 LGPL-3.0。

## 免责声明

本项目仅供学习与研究 Xposed / Android Hook 技术使用，请勿用于商业用途或侵犯第三方合法权益。使用本模块产生的任何后果由使用者自行承担。
