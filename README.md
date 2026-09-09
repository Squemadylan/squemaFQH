# Squema Hook

> 基于 [Modern libxposed API 102](https://github.com/libxposed/api) 的 Xposed 模块，一模块集中管理 5 个目标 App 的增强 / 解锁。

**仓库**：[github.com/Squemadylan/squemaFQH](https://github.com/Squemadylan/squemaFQH) · **作者**：Squema · **当前版本**：1.9.0（versionCode 38）

## 项目命名

| 部分 | 含义 |
| --- | --- |
| **Squema** | 作者英文名 |
| **Hook** | Xposed 模块 |
| 模块名 (`app_name`) | "Squema" |
| 顶级包名 | `com.byterax.phoenix.read`（继承自 ForkRax 老包名） |

## 5 个目标 App

| # | App | 包名 | 解锁能力 |
|---|------|------|---------|
| 1 | **番茄小说** | `com.dragon.read` | VIP / Lynx 横幅 / 个人页推广广告 / 关注/粉丝/获赞 / 昵称「云朵」 / 清空推荐用户 |
| 2 | **红果短剧** | `com.phoenix.read` | VIP / 昵称「云朵」（红果内部复用 `com.dragon.read.user.model.VipInfoModel`，同一套逻辑） |
| 3 | **夸克浏览器** | `com.quark.browser` | URL 安全扫描绕过 / 拦截安全更新 / 拦截用户中心 CMS 广告 / 网盘流畅播提示拦截 / 首页模块过滤 / 屏蔽设置项 / 解锁画质音效 |
| 4 | **小X分身** | `com.bly.dkplat` | VIP（vmppro native + AIDL MemberInfo + 用户中心 UI 字段回填） |
| 5 | **Cherrygram** | `uz.unnarsx.cherrygram` | 捐赠 / 高级功能解锁（5/6 hook 命中 SimpleHookR 12.10.1/70380 Unicode 规则） |

UI（黑金玻璃卡片）一次看全 5 个目标状态：进入 Squema Hook → 5 张卡片显示 SCOPE / LIVE / IDLE。

## 运行环境

| 项 | 要求 |
| --- | --- |
| Android | 8.0+（API 26） |
| 编译 / 目标 SDK | Android 14（API 37） |
| 宿主框架 | LSPosed v2.2.0+（Zygisk 版）；其他支持 libxposed API 102 的现代 Xposed 框架 |

## 技术栈

| 项 | 版本 |
| --- | --- |
| Android Gradle Plugin | 9.2.0 |
| Gradle | 9.5.1 |
| JDK | 17 |
| Android Build Tools | 37.0.0 |
| 语言 | 纯 Java（无 Kotlin、无 AndroidX 业务依赖） |

## 构建

```bash
# Git Bash / WSL 必须先设 Android SDK 路径
export ANDROID_HOME="C:/Users/Squema-Mini/AppData/Local/Android/Sdk"

cd E:/New/squemaFQH
./gradlew assembleDebug --no-daemon
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 安装

```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

## 使用步骤

1. 在 LSPosed Manager（或其他兼容框架）启用本模块
2. 作用域勾选 5 个目标（`android` 是 system 通道）：
   - `com.dragon.read`
   - `com.phoenix.read`
   - `com.quark.browser`
   - `com.bly.dkplat`
   - `uz.unnarsx.cherrygram`
3. **重启手机**生效（不要用 LSPosed 软重启 ——本模块双构造器在 LSPosed v2.2.0 走的是初始化路径，必须等 `zygote` 重启）
4. 首次打开各目标 App，对应 Toast 应弹出（番茄红果：`番茄红果 VIP Hook 成功`；夸克：`SquemaQuark hooking com.quark.browser ...`；小X：`SquemaFQHook onPackageReady xiaox ...`；Cherrygram：`CherryGram VIP Hook 成功`）
5. 在主 App 的状态卡片上能看到 SCOPE / LIVE chip

> **注意**：`adb install -r` 覆盖安装后，LSPosed 偶尔会重置 `enabled=0`，需在 LSPosed Manager 重新勾选 + 强停主 App 再开。

## 模块结构

```
app/src/main/
├── AndroidManifest.xml                  # xposed_description / QuarkSettingsActivity / QuarkConfigProvider
├── java/com/byterax/phoenix/read/
│   ├── HookApp.java                     # Application: XposedServiceHelper 注册 + StatusListener
│   ├── HookInit.java                    # XposedModule 入口; onPackageLoaded / onPackageReady 分发
│   ├── Constants.java                    # PKG_FANQIE / HONGGUO / QUARK / XIAOX / CHERRYGRAM
│   ├── MainActivity.java                # 黑金玻璃卡片 UI; 5 张状态卡 + entrance animation
│   ├── ScopeStatus.java                 # OFF / SCOPED / LIVE 状态机
│   ├── HookStatusStore.java             # SharedPreferences hook_status_v1
│   ├── HookStatusFiles.java             # /data/local/tmp marker + isXxxHooked
│   ├── LspScopeReader.java              # Magisk DB 兜底读 scope
│   ├── RuntimeDetector.java             # libxposed / Zygisk / root 检测
│   ├── service/                          # AIDL 通道（ServiceClient / ServiceProvider）
│   ├── xposed/                           # SystemHookEntry + SystemBootstrap + ... (android 包)
│   ├── quark/                            # QuarkHookMain (~1360 行) + QuarkHooks + Config + Settings
│   ├── xiaox/                            # XiaoXVipHook (~950 行)
│   └── cherrygram/                       # CherryGramVipHook
└── resources/META-INF/xposed/
    ├── java_init.list                    # 仅 HookInit (Modern API 一行)
    ├── scope.list                        # android + 5 个目标
    └── module.prop                       # minApi=102 / targetApi=102 / staticScope=true
```

外部参考：`E:\New\squemaFQH\1\squemaFQH\` 是 v1.9.0 同包源码备份（已加 `.gitignore`，不进仓）。

## Hook 列表（番茄小说 / 红果短剧）

| # | 功能 | 签名特征 | 替换策略 |
|---|------|---------|---------|
| ① | 关闭 Lynx 横幅广告 | 方法名 `willShowLynxBanner` | 返回 `false` |
| ② | 解锁会员 | `VipInfoModel` 全部构造函数 | 篡改 `expireTime / isVip / leftTime` |
| ③ | 屏蔽个人页推广广告位 | 方法名 `canThisPositionShow` | 拉满 `leftTime`，清空 `text` |
| ④ | 伪造关注/粉丝/获赞 | 引用 `"followUserNum = %d, fansNum = %d, ..."` | `followUserNum=5200000` 等 |
| ⑤ | 伪造昵称（同步入口） | 引用 `"doSyncInitUserInfo:%s"` | `userName="云朵"` |
| ⑤b | 伪造昵称（评论用户） | 持有 `CommentUserStrInfo` 字段的类 | `userName="云朵"` |
| ⑥ | 清空推荐用户 | 引用 `"获取推荐用户数据成功"` | `args[0] = null` |

番茄红果使用 DexKit 按特征签名扫描方法（v4.1.6 HookVip 全版本通杀），所有 hook 走 `PROTECTIVE` 异常模式，单条失败不影响其余。

## Hook 列表（夸克浏览器）

`QuarkHookMain.installFromModern` 安装 13+ 条 hook 到 `com.ucpro.*` 内部类：

- URL 安全扫描（`UrlScanManager#u` / `#p`）
- QualityDetect 强制通过（`com.ucpro.feature.webwindow.webview.qualitydetect.c#e`）
- Nezha 风控（`com.ucpro.feature.webwindow.nezha.plugin.f0#f`）
- MSL 网络层（`com.uc.base.net.unet.impl.d5.set`、`e5.set`）
- 锁定设置项开关（getBoolean/putBoolean/getString/putString）
- 首页导航过滤（书城 / 小说 / 学习 / 网盘 / 扫描王 / PPT / 文档）
- 我的页面广告清除
- 网盘流畅播提示拦截
- 拦截夸克升级检查
- 解锁画质音效
- 隐藏夏日任务悬浮

所有开关默认关闭（`/storage/emulated/0/Download/QuarkHook/quark_bypass.cfg` 不存在时 `isEnabled()` 返回 false）。在 Squema Hook 主界面点 **夸克** 卡片进入 `QuarkSettingsActivity` 手动开关。

> v1.9.0 起，**LOCKED_KEYWORDS** 已移除 `ad_reco` / `content_reco` / `reco_switch` —— 首页推荐 feed 不会被 hook 隐藏。

## Hook 列表（小X分身 `com.bly.dkplat`）

VIP 判定集中在 `com.bly.dkplat.cache.UserCache`（vmppro 虚拟化保护 native）。`XiaoXVipHook.installFromModern` 命中：

- `isVipUser()` / `isExpired()` / `getVipType()` / `getExpiredTime()` / `isForbidBannerAd()`
- `MemberInfo(Parcel)` 构造器（跨进程 AIDL 反序列化时直读字段）
- `UserCache` 单例字段直读
- `CRuntime.f6809b` 静态缓存（`CPluginManagerService` 构造时缓存 `isVipUser()`）
- 用户中心 UI 字段回填（`ActivityUserBinding.<init>` / `UserActivity.onResume`）

实测 `getVipType() = 2`（普通会员），过期时间 `4092854400000L`（2099-09-12 00:00 UTC）。

## Hook 列表（Cherrygram `uz.unnarsx.cherrygram`）

源码定位：Cherrygram 12.10.1 的 `DonatesManager` 内部 `didUserDonate*` / `checkAllDonated*` 是真名（被 keep）。

策略：
1. **主**：DexKit 按方法名扫 `didUserDonate` / `didUserDonate2` / `didUserDonateForMarketplace` / `didUserDonateForFeature` / `checkAllDonatedAccounts` / `checkAllDonatedAccountsForMarketplace`，所有 boolean 返回都强制 `true`
2. **兜底**：SimpleHookR 12.10.1/70380 的 6 条 Unicode 规则（类 `m` + 方法 `ⳓ` / `હ` / `㐕` / `㡹` / `ц` / `㫜`），命中即 +1 防御层

实测主 hook 在 12.10.1/70380 上 0 命中（Cherrygram 上游 keep rule 缺失），兜底 5/6 命中（`m.હ(long)` Unicode 名在本版本被 R8 改了）。捐赠功能解锁依赖 Unicode 规则。

## 关键依赖

```gradle
dependencies {
    // libxposed API —— compileOnly: 不打包进 APK
    compileOnly "io.github.libxposed:api:102.0.0"

    // libxposed service (Modern API 101) — 仅模块 app 进程使用，必须 implementation
    implementation "io.github.libxposed:service:101.0.0"

    // AndroidX annotation（仅 service 传递）
    implementation "androidx.annotation:annotation:1.7.1"

    // classic Xposed API（已不被使用 —— HookInit.java_init.list 仅 Modern API）
    compileOnly "de.robv.android.xposed:api:82"

    // DexKit —— implementation: 用于在混淆 dex 中按特征签名定位目标方法
    implementation "org.luckypray:dexkit:2.2.0"
}
```

> **警告**：
> - libxposed 必须是 `compileOnly`，否则与宿主框架冲突
> - DexKit 必须是 `implementation`，否则 `libdexkit.so` 无法加载
> - 不要在 `java_init.list` 加 `IXposedHookLoadPackage` 实现 ——LSPosed v2.2.0 在 Modern API 模式下会因找不到 classic 接口静默失败

## HookInit 双构造器（兼容 LSPosed 1.9.2 ~ 2.2.0）

`HookInit.java` 同时提供两个构造器，LSPosed 反射查找时优先命中旧式签名：

```java
public HookInit(XposedInterface base, XposedModuleInterface.ModuleLoadedParam param) { super(); }
public HookInit() { super(); }
```

缺任何一种 LSPosed 会抛 `NoSuchMethodException` 跳过整个模块加载。

## 关键陷阱

| # | 陷阱 | 后果 |
|---|------|------|
| 1 | libxposed 改为 `implementation` | APK 与宿主框架冲突 |
| 2 | DexKit 改为 `compileOnly` | `libdexkit.so` 不进 APK → `UnsatisfiedLinkError: Could not load libdexkit.so` |
| 3 | `java_init.list` 加 classic `IXposedHookLoadPackage` 实现 | LSPosed Modern 模式静默失败 → 模块完全不加载 |
| 4 | 漏 `onPackageLoaded` / 漏 `PKG_QUARK` 常量 / 漏 Quark-Xiaox 分发 | 5 个目标里只有部分生效，QuarkHook 完全没触发（`SquemaQuark` tag 空） |
| 5 | AGP 8.x 装 `android-37.0` 新格式 SDK | `Failed to find Platform SDK platforms;android-37`，必须用 AGP 9.x |
| 6 | Git Bash 不设 `ANDROID_HOME` | gradle 找不到 SDK，Task `:app:compileDebugJavaWithJavac` 失败 |
| 7 | macOS 元数据 `._*` 文件 | AIDL / dex / XML 资源编译时把这些当源码读 → `error: not find class` / 资源解析失败 |
| 8 | `git add -A` 时包含嵌套 git 目录 | `mode 160000` gitlink 进 index，远程产生 large file 警告 |

## 模块状态显示

Squema Hook 主界面：黑金玻璃卡片，5 张状态卡（番茄 / 红果 / 夸克 / 小X / Cherrygram），每张显示：

- icon（emoji）
- target 名 + 包名
- subtitle（运行中 / 已勾选 / 未勾选）
- chip：`LIVE`（hook 实际跑过） / `SCOPE`（LSPosed scope 已勾） / `IDLE` / `设置`（夸克卡片可点开）

通过 `adb logcat -s SquemaFQHook:SquemaQuark:SquemaFQHook` 看 hook 装载日志。

## 调试

| 目标 | logcat tag | 关键日志 |
|------|-----------|---------|
| 全模块 | `SquemaFQHook` | `onModuleLoaded process=...` / `onPackageReady ...` |
| 番茄红果 | `SquemaFQHook` | `[com.dragon.read] install finished (fullSet=true)` |
| 夸克 | `SquemaQuark` | `[SquemaQuark] hooking com.quark.browser via=loaded` |
| 小X | `SquemaFQHook` | `onPackageReady xiaox loader=...` |
| Cherrygram | `SquemaFQHook` | `[Cherrygram] hooking uz.unnarsx.cherrygram` |

`SquemaQuark` tag **完全为空** → 夸克 Hook 入口没被调用（`onPackageLoaded` 没分发 → 检查 `HookInit.java`）。

## 许可证

GPL-3.0（详见 `LICENSE` 文件）。依赖 DexKit（`org.luckypray:dexkit`）为 LGPL-3.0。

## 免责声明

仅供学习与研究 Xposed / Android Hook 技术使用，请勿用于商业用途或侵犯第三方合法权益。使用本模块产生的任何后果由使用者自行承担。