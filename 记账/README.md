# 哦鲸鲸记账

一个安静、简约的手机记账应用。记录收入与支出，查看每日/每月汇总，设置每月支出上限和攒钱目标，并接入 OpenAI 兼容的大模型接口，让 AI 帮你分析账单、做预算规划。

- 应用名：**哦鲸鲸记账**
- 包名：`com.ohwhale.accounting`
- 版本：`1.1.0`（versionCode 4）
- 支持系统：Android 7.0（API 24）及以上
- 安装包：`dist/哦鲸鲸记账-v1.1.0.apk`

## 功能

### 记账

- 支出分类：餐饮、交通、购物、居住、娱乐、医疗、学习、人情、其他
- 收入分类：工资、奖金、理财、兼职、红包、其他
- 自定义金额、日期、备注，支持修改和删除
- 首页展示今日收支与最近记录
- 「明细」页展示每日分组账单、月度收入/支出/结余、每日支出趋势和分类占比

### 支出上限与攒钱目标

- 设置每月支出上限，首页和明细页展示进度
- 单独设置当月攒钱目标：按「收入 - 支出」计算已经攒下多少，还差多少
- 首页会根据剩下的天数，算出「今天还能花多少」，记账时在金额下方显示这条提示
- 攒钱目标吃紧或支出接近上限时，首页出现提醒；开启系统通知后同步到通知栏
- 可选开启「AI 智能提醒」：预算或攒钱目标吃紧时，让模型写一句提醒
- 应用在启动、回到前台或记账后检查预算与攒钱目标

### 攒钱目标怎么算

- 攒钱目标按自然月保存，每个月可以设不同数额
- 已攒 = 当月收入 - 当月支出
- 剩余可花 = 当月收入 - 攒钱目标 - 当月支出
- 今日可花 = 剩余可花 / 剩余天数；如果为 0，会提示今天先别花了
- 如果照当前速度到月底攒不到目标，会提示「现在够了，月底会差多少」

### AI 账单分析

在「我的 → 大模型接口」中填入任意 OpenAI 兼容接口：

| 服务 | Base URL 示例 | 模型示例 |
| --- | --- | --- |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` |
| Ollama（局域网） | `http://192.168.1.10:11434/v1` | `qwen2.5:7b` |

- 只需要填到 `/v1`，应用会自动补上 `/chat/completions`；也可以直接填完整地址
- 支持「测试连接」，成功后再保存
- 可让 AI 分析本月消费、给出节省建议、制定下月预算，也可以自由提问
- 请求只包含账单摘要和你的问题，不会上传通讯录、位置等无关信息

## 安装

1. 把 `dist/哦鲸鲸记账-v1.1.0.apk` 发送到手机。
2. 在手机上点击安装；如系统提示，请允许「安装未知来源应用」。
3. 打开「哦鲸鲸记账」即可使用。

APK 已使用本地 release keystore 签名（APK Signature Scheme v1/v2/v3），可直接安装。

启动图标使用用户提供的原始蓝发角色图，补成正方形 PNG，未裁切或改画；应用内品牌插画已替换为配套角色图。

## 构建

构建不依赖 Gradle，`build.ps1` 直接调用 Android SDK 的 `aapt2`、`javac`、`d8`、`zipalign`、`apksigner`。

### 环境

- JDK 11+（本项目在 JDK 21 下构建通过）
- Android SDK：
  - `platforms/android-35`
  - `build-tools/35.0.1`
  - `platform-tools`（可选，用于 adb）

### 命令

```powershell
# 默认 SDK 路径为 D:\Android\Sdk
.\build.ps1

# 或指定 SDK 与版本
.\build.ps1 -Sdk 'C:\Android\Sdk' -VersionName '1.1.0' -VersionCode 4
```

产物：

- `dist/OhWhale-1.1.0.apk`
- `dist/OhWhale-latest.apk`

如果项目路径包含中文，脚本会自动创建 ASCII 目录联接 `C:\OhWhaleBuildSrc` 供 `aapt2` 使用（`aapt2` 在 Windows 上无法打开中文路径）。

打包阶段会把 `resources.arsc` 以未压缩方式写入，并在签名前执行 `zipalign -f 4`；这是 Android 11+ 安装时避免 `-124` 错误的要求。

### 签名

首次构建时如果 `keystore/ohwhale-release.jks` 不存在，脚本会根据 `keystore/keystore.properties` 自动生成。请妥善保管该 keystore；后续升级必须使用同一签名。

## 测试

```powershell
node tests/core.test.js
node --check app/src/main/assets/js/app.js
```

`tests/core.test.js` 覆盖日期/月份、金额格式化、月度汇总、分类统计、按日分组、预算阈值、攒钱目标与每日额度、数据清洗、接口地址规范化、AI 响应解析、隐私快照等逻辑。

`tools/` 下带有本地调试脚本：

- `tools/shot.js`：通过 CDP 截图（390 × 844）
- `tools/ui-smoke.js`：记账弹层、预算、提醒、记录顺序、备份脱敏的冒烟测试
- `tools/mock-ai.js`：本地 OpenAI 兼容 mock 接口
- `tools/ai-smoke.js`：AI 配置、测试连接、对话的端到端测试

这些脚本需要先启动带 `--remote-debugging-port=9222` 的 Edge，并加载 `app/src/main/assets/index.html`。仅用于开发验证，不参与 APK 打包。

## 项目结构

```text
app/src/main/
  AndroidManifest.xml
  java/com/ohwhale/accounting/MainActivity.java   # WebView 壳 + 原生桥
  assets/
    index.html                                    # 单页结构
    css/app.css                                   # 蓝发角色主题样式
    js/core.js                                    # 纯逻辑：汇总/预算/攒钱/AI 消息
    js/app.js                                     # 页面、弹层、交互
    img/                                          # 角色插画
  res/                                            # 图标、主题、字符串
build.ps1                                         # 无 Gradle 构建脚本
tests/core.test.js                                # Node 逻辑测试
dist/                                             # 构建产物
keystore/                                         # 本地签名材料
```

## 隐私说明

- 账单、预算、AI 配置只保存在应用私有目录（`ohwhale-state.json`），不上传服务器。
- API Key 以明文保存在应用私有存储中；请使用权限受控的手机，并只填写你信任的接口。
- 导出/复制备份时会自动移除 API Key，避免分享备份时泄露密钥。
- 发起 AI 请求时，账单摘要会发送到你配置的接口；不发起请求就不会联网。
- 应用无账号、无广告、无统计 SDK。

## 安装问题

### 小米/红米提示「安装失败 (-124)，该安装包与您的系统不兼容」

`-124` 通常不是手机版本太旧，而是 APK 中的 `resources.arsc` 被压缩或没有按 4 字节对齐（Android 11+ 的安装要求）。本项目已改为未压缩写入 `resources.arsc` 并执行 `zipalign -f 4`，同时保留 v1/v2/v3 三种签名。

如果仍看到旧提示，请确认安装的是最新文件，删除手机里旧的 APK 后重新传输；仍不行可先卸载旧版本，再重启手机安装。

## 当前限制
- 数据只存在本机，暂不提供云同步；换机请使用「导出备份 / 导入备份」。
- 每月支出上限是全局设置，修改后会影响所有月份的预算展示；攒钱目标按月份分别保存。
- AI 提醒依赖应用被打开时检查，不是常驻后台服务。
- AI 回答质量取决于你选择的模型和接口，内容仅供记账参考。

---

让每一笔都更从容。
