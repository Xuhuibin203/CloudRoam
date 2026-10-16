# CloudRoam 交接文档：无 Root 纯 App 技术框架

> 供下一次对话（任何模型）无缝接手。日期：2026-10-16
> 用户硬约束：**不用 root、不用虚拟机/容器/云机、做成纯 App**；目标：所有 APP（含微信/企业微信）读到虚拟定位；后续商业化（自用 → 租赁/售卖）

---

## 一、项目现状快照（已验证事实）

- 仓库：`git@github.com:Xuhuibin203/CloudRoam.git`（main 分支，最新 commit b2491c0）
- 本地构建环境：`E:\android-build-tools\`（JDK 21 + Android SDK 35/36 + Gradle 8.11.1 wrapper，依赖走阿里云/腾讯镜像）
- M1 已完成并真机验证（小米 15，Android 16，未 root）：
  - COMPAT 引擎（系统 Mock Provider，1Hz 注入）✅ 系统层生效（`dumpsys location` 可见 mock 坐标）
  - FULL 引擎（libxposed 101 Hook 层）代码就绪但**从未真机验证**（需 root 环境）
  - UI/前台服务/快捷磁贴/环境自检/协议页/高德地图选点（Key 在 local.properties）✅
- 实测结论：**兼容模式对微信/QQ 无效**——腾讯定位 SDK：WiFi/基站优先 + 拒绝带 isMock 标记的 GPS
- 双 AI 自查已完成：2 个 P0（失败安全兜底、getCurrentLocation 签名探测）+ 3 个 P1 已全部修复入库

## 二、硬约束与既有结论

| 路线 | 结论 |
|---|---|
| root（小米解锁） | 用户否决：需清空数据、等待期长 |
| 应用虚拟化容器（VirtualXposed 系） | 用户否决：属"虚拟机"范畴 |
| redroid 云机 | 用户否决：同上 |
| 兼容模式（Mock Provider） | 已建成；对腾讯系应用无效（isMock + WiFi/基站泄露） |
| **APK 重打包内嵌 Hook（LSPatch 路线）** | **唯一满足"无 root + 纯 App"约束的真实路径，待实施** |

无 root 时 APP 无法修改其它 APP 进程、无法伪造系统级 WiFi 扫描/基站数据——这是 Android 安全模型决定的，任何"纯 App"方案都必须把代码送进目标 APP 进程，只剩 LSPatch 一条路。

## 三、LSPatch 路线技术框架（待实施）

### 3.1 核心原理
LSPatch（GitHub: LSPosed/LSPatch，Apache-2.0）在**不修改系统**的前提下，把 Xposed 模块嵌入目标 APK：反编译目标 APK → 注入 LSPatch loader 与模块 → 重签名安装。补丁后的微信进程内，模块以 Xposed API 运行，**权限等同微信本体**。

### 3.2 CloudRoam 的组件架构
```
CloudRoam 主 App（管理器）
├── 补丁引擎：集成 LSPatch（jar 库，支持免 root 打补丁）
│   ├── 输入：已安装微信/企微的 APK（pm path 提取）
│   ├── 嵌入：CloudRoamHook 模块（复用现有 LocationHooks.kt 的全部逻辑）
│   └── 输出：重签名 APK → 引导用户卸载原版 → 安装补丁版
├── 配置下发：exported ContentProvider（签名级权限保护）
│   └── 补丁内模块实时读取：enabled / lat / lng / 模式 / WiFi-基站底料
├── 既有资产全复用：选点 UI、前台服务、磁贴、位置库（Settings.kt）
└── 模块内 Hook 能力（= M2 清单，与 root 版完全同一份代码）：
    LocationManager 全家 + GNSS/NMEA + isMock 隐藏
    + WiFi 扫描结果伪造 + 基站信息伪造（这两项在补丁内可行，权限来自微信）
```

### 3.3 关键工程决策点（下次对话优先讨论）
1. **签名与自校验**：重签名 ≠ 微信官方签名。微信内部有签名校验（支付/设备验证可能异常）；企微校验更严。缓解：LSPatch 的"便携模式"（原版共存）vs 覆盖安装模式的取舍；需要真机实测微信登录/收发消息/位置功能
2. **升级维护**：微信每次升级需重打补丁 → 产品要做"一键更新补丁"流水线（监听目标 APP 版本变化）
3. **配置通道**：ContentProvider 方案 vs 文件共享方案，需在补丁内实测
4. **WiFi 底料**（AI 自查指出的最大技术瓶颈）：编造 AP 过不了腾讯云端比对；候选方案：a) 离线采集虚拟城市的真实 AP 数据集 b) hook 层让"已连接 WiFi 信息"与扫描结果一致 c) 承诺降级（位置一致但 WiFi 名单可能有破绽）
5. **分发与商业**：官网 APK 直装；合规条款（PRD FR-08）必须保留——考勤作弊场景有司法判例风险，商业定位锚定隐私保护/开发测试

### 3.4 里程碑建议
- **N1**：集成 LSPatch，跑通"微信补丁 + 位置 hook 生效"（先只 hook LocationManager + isMock），真机验证微信发送位置
- **N2**：补丁内 WiFi/基站伪造（M2 代码复用）+ 配置实时下发 + 企微实测
- **N3**：一键更新流水线 + 作用域管理 + 商业化包装（授权/订阅）

## 四、环境与凭据备忘

- 构建命令：`E:\android-build-tools\jdk\jdk-21.0.12.1+1` 设为 JAVA_HOME → `.\gradlew.bat assembleDebug`
- AMap Key：已配在 `local.properties`（勿提交）
- 阿里云服务器 101.37.29.106（2C2G，宝塔面板，已配 SSH 公钥 `E:\android-build-tools\ssh\cloudroam_key`）——本路线暂用不上，保留
- 小米 15 测试机（未 root）：`adb install` 可用，USB 调试已开
- 用户已注册高德开发者账号

## 五、合规红线（不可删除）

用户协议必须声明：仅限隐私保护等合法用途；禁止用于考勤作弊、平台刷单、游戏作弊；违规责任自负。商业售卖时增加目标应用封号风险明示。
