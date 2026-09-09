# Squema Hook Project Memory

## 项目状态

**Squema Hook** 是一个 libxposed Modern API 102 Xposed 模块，hook 5 个目标 App：番茄小说、红果短剧、夸克、小X分身、Cherrygram。当前 versionCode 38 / versionName 1.9.0。

源码在 `E:\New\squemaFQH\`，v1.9.0 参考源备份在 `E:\New\squemaFQH\1\squemaFQH\`（已 gitignore，不进仓）。

## 工作流（同步 v1.9.0 + 加新目标的硬性 checklist）

### 新增目标 App 时的必做项（缺一不可，否则 hook 不生效）

1. `app/src/main/java/com/byterax/phoenix/read/Constants.java` — 加 `PKG_*` 常量
2. `HookInit.java` —
   - import 对应的 hook 类
   - **必须** override `onPackageLoaded`（处理 Quark 这种在 Application.attach 之前的）
   - `onPackageReady` 里加对应分支
   - 顶部声明 `PKG_*` static final 字段
3. `app/src/main/resources/META-INF/xposed/scope.list` — 加包名
4. `HookStatusFiles.java` — 加 `isXxxHooked()` + `markTargetHooked` 分支 + tmp 文件常量
5. `HookStatusStore.java` — `key()` 加 pkg 名映射
6. `MainActivity.java` — 加 card 字段 + bindCard 调用 + prepareEntrance / playEntrance 数组
7. `activity_main.xml` — 加 `<include id="@+id/card_xxx" ...>`
8. `strings.xml` — 加 `target_xxx` 标题
9. `HookStatusReporter`（AIDL 上报）— 实测**不可用**（system_server 通道未启动），但保留以备 future use

### 双构造器（兼容 LSPosed 1.9.2 ~ 2.2.0）

`HookInit` 必须同时提供：
```java
public HookInit(XposedInterface base, XposedModuleInterface.ModuleLoadedParam param) { super(); }
public HookInit() { super(); }
```
缺任一种 LSPosed 抛 `NoSuchMethodException` 静默失败。

### java_init.list 规则

**只放 Modern API 入口**：
```
com.byterax.phoenix.read.HookInit
```
**不要**加 classic `IXposedHookLoadPackage` 实现 ——LSPosed v2.2.0 在 Modern 模式下找不到 classic 接口会静默失败，整个模块加载失败（不只那个 classic 入口失败）。

## 验证 hook 真正生效的标志

按 `adb logcat -s SquemaFQHook:* SquemaQuark:*` 看 tag：
- 番茄红果：`SquemaFQHook: [com.dragon.read] install finished`
- 夸克：`SquemaQuark: [SquemaQuark] hooking com.quark.browser via=loaded enable=true` + 后续 13+ 条 `hooked ...` 行
- 小X：`SquemaFQHook: onPackageReady xiaox loader=...`
- Cherrygram：`SquemaFQHook: [Cherrygram] result: primary=0 hook(s) legacy=6/6`

**`SquemaQuark` tag 完全为空** → 夸克 Hook 入口没被调用。99% 是 HookInit 漏了 `onPackageLoaded` / 漏 `PKG_QUARK` 常量 / 漏 `QuarkHookMain.installFromModern` 调用。

## 关键陷阱（实际踩过的）

### 1. Unicode 转义写错 codepoint
Cherrygram SimpleHookR 规则的 `m.હ(long)` 是 **U+0AB9**（Gujarati Letter HA with nukta），不是 **U+0A99**（Gujarati Letter HA）。两个字符**长得几乎一样**，UTF-8 字节只差末位（`99` vs `b9`）。从表格反推 codepoint 不可靠，**必须直接从 dex 字节反查**。

### 2. 同步 v1.9.0 源码容易漏 HookInit 分发
只同步 `res/` 和 java 主类时容易漏 `HookInit.java` 的：
- `onPackageLoaded` 回调
- `PKG_QUARK` / `PKG_XIAOX` 常量
- `QuarkHookMain.installFromModern` 调用
- `import` 语句
**核对方法**：diff `1/squemaFQH/app/src/main/java/com/byterax/phoenix/read/HookInit.java` vs `app/src/main/java/com/byterax/phoenix/read/HookInit.java`。

### 3. git add -A 陷阱
- 嵌入 git 仓库（`1/squemaFQH/.git/`）会被识别为 `mode 160000` gitlink 进 index
- macOS 元数据 `._*` 在源码里到处都是
- 临时 ZIP（`squemaFQH.zip`、`base.apk`）容易进 working dir

**提交前必看 `git status --short`**，看每一个文件再 add。

### 4. AndroidManifest / scope.list / java_init.list 必须同步
- `scope.list` 加新包 ≠ `MainActivity` 显示新卡片 —— 必须**同时**改 5 个文件
- scope 漏加 → LSPosed Manager 看不到这个包 → 没法勾选 → hook 不触发

## LSPosed 数据库直接操作（当 LSPosed Manager 不可用时）

`/data/adb/lspd/config/modules_config.db` SQLite 表：
- `modules(module_pkg_name, apk_path)`
- `modules_state(module_pkg_name, enabled, ?, ?)` —— `enabled=1` 必须显式设
- `scope(module_pkg_name, app_pkg_name, user_id)` —— user_id=0

操作流程：
1. `adb shell "su -c 'cp /data/adb/lspd/config/modules_config.db /sdcard/'"`
2. `adb pull /sdcard/modules_config.db .`
3. 用 Python sqlite3 直接改
4. `adb push` 回去 → `chown 1000:1000` → `chmod 660` → 删 `-shm` `-wal`
5. **必须重启手机**（`adb reboot`）让 LSPosed 重读，不能用 `am restart`

## 死代码清单（不要碰，集成时容易看错）

- `app/src/main/java/com/byterax/phoenix/read/xposed/` (4 文件) — system_server 通道的 classic Xposed 实现，**运行时不会触发**（HookInit 没 override `onSystemServerStarting`），但 `xposed/SystemUserService.java` 仍依赖 `de.robv.android.xposed:api:82`
- `app/src/main/java/com/byterax/phoenix/read/HookStatusReporter.java` — AIDL 上报客户端，调用的远端服务**永远 null**（system_server 通道未启动）
- `app/src/main/java/com/byterax/phoenix/read/service/` (2 文件) — 同样依赖未启动的 AIDL
- 整个 AIDL 上报链没在用户实际功能里起作用，UI 状态读 `/data/local/tmp/squema_fq_hook.*` marker 走文件路径

## 构建命令（Git Bash + Windows）

```bash
export ANDROID_HOME="C:/Users/Squema-Mini/AppData/Local/Android/Sdk"
./gradlew assembleDebug --no-daemon
# 产物：app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 装机验证流程

1. 装 APK
2. LSPosed Manager 启用模块 + 勾选 5 个 scope
3. **重启手机**（LSPosed v2.2.0 zygote 注入必须重启）
4. 打开 Squema Hook 主 App → 5 张卡片应显示 SCOPE
5. 依次打开 5 个目标 App：
   - 番茄红果：Toast「番茄红果 VIP Hook 成功」
   - 夸克：状态卡变 LIVE + Quark 内部功能
   - 小X：状态卡变 LIVE + VIP 字段
   - Cherrygram：状态卡变 LIVE + 捐赠功能开关全开

## 1.9.0 主线（项目根）

- 已 commit `042c861` 是最新版本
- v1.9.0 实现 5 个目标 hook 完整
- Cherrygram 6/6 命中，Unicode codepoint 已修
- Quark 13+ hook，首页 LOCKED_KEYWORDS 已移除非含 reco 三项
- MainActivity 5 张卡片绑定，entrance animation 入场
