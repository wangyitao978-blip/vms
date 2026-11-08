# vmios / SweetVM 项目交接记录（截至 2026-11-09）

> 用途：将 ChatGPT 项目对话中保留的技术上下文交给 Codex，继续排查 Android 上 SweetVM 启动 iOS 16 后无法显示正常系统界面的问题。
>
> **记录完整性声明：** 本文根据当前可访问的项目历史摘要、近期对话片段和当前对话整理。此前多个独立会话的完整逐字记录并未全部暴露给当前会话，因此本文不是逐字聊天导出；对可确认的信息尽量完整收录，并明确区分事实、历史观察和待验证推断。若需要逐字无遗漏的原始聊天记录，仍需从 ChatGPT 对话历史另行导出/复制。不要把本文中标为“待验证”的内容当成已证实事实。

## 1. 当前首要目标

当前唯一最高优先级目标：**让虚拟机正常显示完整、可用的 iOS 系统界面（SpringBoard / 主屏幕等）**。

当前不要把精力放在紫色方块、色块探针、显示美化或性能微调上。用户反复强调：目前连正常系统界面都没有看到，先解决“能显示正常 iOS 界面”这一根本问题。

当前表现经历过：
- 虚拟机可以进入较深的 XNU / iOS 启动阶段。
- 日志显示 backboardd 和 SpringBoard 已启动，且创建了窗口、发生 Metal/CA 绘制和 framebuffer swap。
- 实际 Android 端却没有呈现正常 iOS 系统画面；用户曾观察到长时间停留在 Apple / 启动 logo，等待约 15 分钟仍无正常界面。
- 另一次运行约 18 分钟后画面变化，随后黑屏，右侧出现紫色方块。
- 后续讨论中，用户明确要求不要先处理紫色方块；它不是当前主线。

## 2. 用户的硬性要求（必须遵守）

1. **严格忠于原始仓库方案。** 用户明确要求从头制作 APK、payload、镜像时，完全按照原始仓库的方案执行：
   - APK 不擅自内置 XNU 等额外内容；
   - payload 不擅自删减补丁或改动原始补丁集；
   - 镜像按原始方案制作；
   - 不要为了“优化”而擅自改变原项目结构、流程或内容。
   - 只有在原始方案制作出的结果出现明确问题后，才讨论有证据支持的最小修改。
2. **不要混淆用户仓库和上游仓库。**
   - 用户自己的仓库：`wangyitao978-blip/vms`。
   - 原始上游仓库 owner/repo：历史对话中提到 `lq686`，但完整仓库名/上游关系需重新核实。
   - 用户复制上游项目到自己的仓库，是为了使用 GitHub 云构建并继续调试。
3. **命令统一使用 Windows CMD。** 用户在 Windows 端执行的命令必须使用 CMD 语法；不要在 CMD 和 PowerShell 之间反复切换。若确实必须使用 PowerShell，先解释原因并征得同意。
4. **优先利用已有证据。** 用户上传次数有限。不要重复要求上传已经解析过的 `guest-ring.bin`、`sweetvm-current.txt` 或重复采集相同日志，除非能明确说明新的采集目的。
5. **不要未经同意替换文件或启动测试。** 目前新旧 payload 仍在比较阶段。不要让用户先替换 payload、重启虚拟机或刷入新镜像，直到比较结果和构建来源得到分析。
6. **不要把猜测写成事实。** 每个诊断结论需注明日志证据、源码证据或仍待验证。
7. 用户希望尽快解决问题，避免无依据地进行大量反复试验。

## 3. 仓库与工作流背景

- 用户仓库：`https://github.com/wangyitao978-blip/vms`
- 默认分支：历史记录显示为 `main`。
- 上游原始项目曾被称为 `lq686`；请通过仓库文件、remote 配置或 GitHub 历史进一步确认完整来源，不要擅自假设。
- 用户曾在 macOS 本地目录 `~/Desktop/vms-source` 操作，也曾在 Windows/WSL 环境操作。WSL 提示符曾显示：
  `anlall@DESKTOP-BCOQ4L2:/mnt/c/Windows/system32`
- Windows 下载/ADB 目录曾使用：
  - `D:\\Download`
  - `D:\\Download\\adb`
- Android 包名：`dev.vmios.sweetvm`
- 用户通过 GitHub Actions 云构建复制后的仓库，不是要切换回上游仓库直接构建。
- 不要把用户仓库 `wangyitao978-blip/vms` 说成上游原始仓库。

## 4. 历史工作脉络（按现有摘要可还原的顺序）

### 4.1 复制仓库、原始构建流程与镜像制作

在标题为“判断启动问题”及后续编号会话中，用户要求从原始仓库方案重新制作所有文件。用户特别强调：
- APK、payload、镜像都必须遵照原始仓库流程；
- 不要删减补丁；
- 不要把 XNU 等额外内容内置到 APK；
- 镜像仅按原始方案制作；
- 有问题再针对证据修改。

用户还要求后续同主题新对话必须记住这一要求。此约束在本文件中再次记录，Codex 必须遵守。

### 4.2 初期启动问题：停留在启动日志 / logo

用户报告过无法看到正常 iOS 桌面，曾怀疑卡在以下启动日志附近：

```
iBoot version: sweet-shim
iBoot Stage 2 version: sweet
"name" not a kext
"AAPL, phandle" not a kext
SWEET AMFI: built-in interface registered (XML entitlement...)
calling mpo_policy_init for vnguard
Security policy loaded: Guarded vnode policy (vnguard)
SWEET AMFI: built-in interface registered
...
```

之后用户提供了更长的启动输出，包含：
- `calling mpo_policy_init for vnguard`
- `Security policy loaded: Guarded vnode policy (vnguard)`
- `SWEET: trust caches unavailable (Image4/AMFI not installed)`
- `keepsyms boot-arg specified; keeping linkedit segment for symbols.`
- `No POST boot-arg set.`
- `SWEET: starting device-tree platform expert`
- `SWEET: platform expert registered`
- `SWEET AKS: AppleKeyStore registered`
- `SWEET WDT: IOWatchdog...`

这些日志表明启动进入 XNU 深层初始化，但不能单凭这些行断定系统卡在该位置。后续 ring log 证据显示 backboardd / SpringBoard 实际启动，因此不能继续将早期日志最后一行当作唯一卡点。

### 4.3 多核 / 启动速度问题

用户问过为什么启动很慢，是否配置只用了一个 CPU，以及原项目是否提到多核相关问题。

现有解析记录：
- XNU 启动日志显示初始化了 **2/2 CPU**。
- 这与“只用了一个 CPU”的猜测不符。
- `NOTES.md` 中曾出现 `payload-4cpu-perf6.bin` 这一旧测试文件名，但这不是用户当前使用的 payload。
- 不要把旧的 4 CPU 测试文件名误当成当前产物，也不要据此要求用户切换文件。
- 当前启动慢可能涉及 Guest 系统初始化、渲染、present 或其它路径，现有证据不足以归因于 CPU 数量。

### 4.4 Android / ADB 检查

用户曾执行过类似命令（命令格式以 CMD 为准）：

```cmd
adb shell pm list packages | findstr /i sweet
```

返回：
```
package:dev.vmios.sweetvm
```

也曾检查进程：
```cmd
adb shell ps -A | findstr /i "sweetvm qemu"
```

输出中出现过：
```
u0_a464 ... dev.vmios...
```

说明 Android app / 相关进程存在，但进程存在不代表 Guest framebuffer 已经正确显示。

曾在 app 私有目录中检查 Guest RAM 文件：
```cmd
adb shell "run-as dev.vmios.sweetvm sh -c 'dd if=files/vm/guest-ram.bin bs=4 skip=561025024 count=1 2>/dev/null | od -An -tx4'"
```
当时结果：
```
00000000
```
该单点值不能单独说明整个 framebuffer 内容或显示路径正常与否。

用户也通过 ADB 读取过 root image 尾部数据，历史命令涉及：
`files/ios16-root.img`。不要据此推断镜像损坏；当时尚未得出这类结论。

### 4.5 guest-ring.bin 与日志解析

用户提供过 `guest-ring.bin`，已解析。**不要要求用户重复上传同一文件。**

解析结果：
- 文件格式：`SWEETLOG` v1 环形日志。
- 文件大小：319,701 bytes。
- 解析出：5,429 行。
- 编码：UTF-8。
- 解析记录显示日志完整，未覆盖旧内容。

重要日志事实：
1. XNU 启动后初始化 **2/2 CPU**。
2. `backboardd` 和 `SpringBoard` 已启动。
3. SpringBoard 创建多个窗口，包括壁纸、状态栏和根场景。
4. Metal 生成过全屏 `1125×2436` 渲染。
5. 至少提交过 14 次 framebuffer swap。
6. `CAWindowServer serverIfExisting` 在 backboardd / SpringBoard 中返回空对象。
7. 部分全屏绘制耗时约 3.5–4.5 秒。
8. `IOMobileFramebuffer` present 调用返回 `0x10000003`。
   - 之前分析认为该 Mach 错误码对应 `MACH_SEND_INVALID_DEST`。
   - 但内核日志中的 swap 序号仍然推进，因此不能仅凭该返回值断定所有 present 都失败。
9. Guest 显示电源状态出现过 `display off`、`display on`，最后再次为 `display off`。
   - 最后一条状态为 off。
   - 此后仍有 vsync，但没有新的 Metal 绘制记录。
   - 黑屏可能部分与 Guest 显示电源管理有关，但仍需验证，不能作为定论。
10. `sweet_probe_panel()` 会主动写测试色块：
    - surface 1 左上角 200×200；
    - 颜色 `0x00ff00ff`；
    - backboardd 与 SpringBoard 分别执行过一次，返回 0。
    - 用户看到的紫色方块可能来自此探针，但当前不是首要处理对象。

### 4.6 sweetvm-current.txt / Android framebuffer reader 解析

用户曾提供 `sweetvm-current.txt`，已分析过。不要重复索要同一份文件。

指标含义：
- `fps`：Android framebuffer 被发现并复制到 Bitmap 的帧速率。
- `ticks`：reader poll loop 轮询次数。
- `copies`：engine.frames 累计。
- `copy_avg_us`：平均整帧复制耗时。
- `copy_max_us`：最大一次整帧复制耗时。

源码观察：
- `VmEngine.java` 非 offload 模式使用 `changed()` 对 framebuffer 每 16 个 uint32（即每 64 bytes）抽样一个 word。
- 发现抽样值变化才复制整帧。
- 因此：
  - `ticks` 增长仅说明 Android reader 仍在轮询；
  - `fps=0` 且 `copies` 约 7 分钟不增长，表示抽样机制未检测到显存变化；
  - 但抽样机制可能漏掉没有命中抽样点的局部变化。
- 整帧复制平均约 1 ms，最大约 3.6 ms。
- 现有数据不支持“Android 整帧复制性能太慢是主要瓶颈”的结论。
- 需要把 Guest 侧是否真正更新 framebuffer、QEMU 共享内存是否变化、Android reader 是否检测到变化分开验证。

### 4.7 framebuffer / present / Metal 源码路径分析

已查看或讨论过以下源码：

#### `vm/metal/SweetMetal.m`

现有分析记录：
- `sweet_ca_flush()` 在 CA host draw flush 时同步调用 wire flush。
- `sweet_present_surface` 涉及：
  - `IOMobileFramebufferOpen`
  - 准备 swap record
  - `IOConnectScalarMethod` index 4
  - `IOConnectCallStructMethod` index 5
- `sweet_present_pending()` 在 commit / commitAndWaitUntilSubmitted 中提交 `sweet_last_surface`。
- `endEncoding` 中：
  - 执行 `sweet_ca_flush`；
  - 若 stage 有效，将 host stage 像素复制到 drawable；
  - 若 enc surface 有效且尺寸至少约 1000×2000，则记录最后 surface，之后提交。
- 如果 `IOMFBOpen` 失败，会打印类似：
  `present open service=... kr=... conn=...`
- 历史 `NOTES` 曾记录 IOMFBOpen 有时失败；Guest WindowServer 可能本来就负责正常 present，因此插件自定义 present 不一定成功。
- 不要把插件自定义 present 路径和系统原生 WindowServer present 路径混为一谈。

#### `vm/qemu/sweet_fb.c`

现有分析记录：
- Guest present command 会使 QEMU 复制 descriptor。
- completion sequence/status 会写回共享 RAM。
- native present/offload 与普通 framebuffer 模式走不同路径。
- 需要检查当前实际配置和调用路径，不能假设所有运行都走同一模式。

#### Android `CaCompositor.java`

现有分析记录：
- `onFlush()` 记录 frame/draws/px。
- `onDraw()` 如果目标 surface 不可用、not ready 或尺寸无效，会 drop。
- 需要区分 Guest 已绘制、Host compositor 收到绘制、Android Surface 实际显示这几个阶段。

### 4.8 诊断补丁 0098

已读取补丁：
`xnu/patches/0098-sweet-fb-copy-diagnostics.patch`

修改文件：
`iokit/Kernel/arm/SweetFramebuffer.cpp`

修改函数：
`AppleCLCD::present`

补丁添加 `SWEET FB COPY:` 日志，用于记录：
- layer / id
- map / src / panel / phys 地址
- bpe
- 尺寸
- rect
- srcxy
- len
- last
- center
- srcpx
- panel0
- copy
- offload

并在中心点 `(562,1218)` 比较复制前后的像素，打印：
`center-after src=... panel=... copied=... offload=...`

诊断意图：区分
1. 源 IOSurface 是否有像素；
2. Guest framebuffer copy 是否发生；
3. Android framebuffer readback 是否取得内容。

重要：
- 该补丁属于**诊断补丁**，不是已经确认的最终修复。
- 之前的运行没有看到 `SWEET FB COPY` 记录，故当时无法确认内核是否真的包含/执行了 0098。
- 不要擅自删掉该补丁或改动补丁集。
- 下一步需要查明 XNU workflow 是否实际应用补丁，以及 payload workflow 是否打包了对应构建产物。

## 5. GitHub Actions / 构建记录

用户仓库为 `wangyitao978-blip/vms`。近期相关 GitHub Actions 信息：

### 5.1 已知 run

- Workflow 名称：`Build upstream-faithful Android GPU payload`
- Run #11
- Run ID：`42513718545`
- Commit：`75aa2dee84c7ee04aaa50fea1e965c5a9eb5f0ba`
- 时间：2026-11-08 19:10:44 UTC
- 结果：success
- Artifact 名称：`vmios-host-raster-payload`
- Run 页面：
  `https://github.com/wangyitao978-blip/vms/actions/runs/42513718545`

另一个相关 workflow：
- Workflow：`xnu`
- Run #36
- Run ID：`42513328001`
- 同一 commit：`75aa2dee84c7ee04aaa50fea1e965c5a9eb5f0ba`
- 时间：2026-11-08 19:06:20 UTC
- 结果：success

注意：此前还提到过约 17:34 UTC 的第 8 次构建。它可能是不同 run。不能把 17:34 UTC 第 8 次构建与 19:10 UTC run #11 混为一谈。用户当前 payload 实际来自哪个 artifact，仍需根据文件下载记录或构建产物核对。

### 5.2 xnu.yml 已知流程

已查看过 `.github/workflows/xnu.yml` 的部分内容：
- `workflow_dispatch` inputs：
  - `macos_version` 默认 13.5
  - `kernel_config` 默认 DEVELOPMENT
  - `machine_config` 默认 SWEET
  - `keep_going` 默认 true
- push 触发路径：
  - `xnu/patches/**`
  - `.github/workflows/xnu.yml`
- `Fetch build script` 步骤把 `xnu/patches/*.patch` 和 `*.sh` 复制到 dxb/patches。
- `Build` 步骤执行 `bash build.sh`。
- 上传 artifact：
  - `xnu-buildtree`
  - `xnu-kernel`

初步推论（需核实）：
- 如果 run #36 确实基于 commit `75aa...` 且 workflow 正常执行，则 0098 文件会被复制进构建树。
- 但“文件被复制”不等于“补丁成功应用”，还必须看 build.sh 日志中的 patch apply 记录。
- 还必须查 payload workflow 下载的是哪个 XNU artifact / commit，确认新 payload 内嵌的 kernel 来自这次 XNU 构建。

### 5.3 相关 commit

Commit：
`75aa2dee84c7ee04aaa50fea1e965c5a9eb5f0ba`

Commit message：
`fix: correct framebuffer copy diagnostics patch hunk counts`

需检查：
- commit 中 0098 补丁内容是否正确；
- XNU run #36 的 job logs 是否显示补丁应用成功；
- GPU payload run #11 的 job logs 是否下载了 run #36 对应的 artifact；
- artifact 内 payload 中实际嵌入的 kernel 是否与预期一致。

不要只因为 workflow 显示 success 就断言补丁已生效。

## 6. 当前 payload 比较（当前对话最新状态）

用户明确说明：他一直使用的是最后一次 GitHub 构建产物 `vmios-host-raster-payload.zip` 内的：

`sweet-qemu-gpu-payload.bin`

**不是** `payload-4cpu-perf6.bin`。

旧文件名 `payload-4cpu-perf6.bin` 只出现在旧的 `NOTES.md` 测试记录中，不是用户当前文件。不要再把它说成用户正在使用的 payload。

### 6.1 用户已给出的 SHA-1

Windows CMD 执行：

```cmd
certutil -hashfile D:\\Download\\sweet-qemu-gpu-payload.bin
```

结果：
```
SHA1 的 D:\\Download\\sweet-qemu-gpu-payload.bin 哈希:
9b2c563f5cffe6e73e1dd453abd3f96cff9aa0f8
CertUtil: -hashfile 命令成功完成。
```

旧文件：

```cmd
certutil -hashfile D:\\Download\\sweet-qemu-gpu-payload-old.bin
```

结果：
```
SHA1 的 D:\\Download\\sweet-qemu-gpu-payload-old.bin 哈希:
3c39efab2cc62bf37da93e2a8edf2c10fd59f330
CertUtil: -hashfile 命令成功完成。
```

结论：新旧文件 SHA-1 不同，说明文件内容不是完全相同；但仅凭哈希不能判断差异范围、差异内容或哪个文件正确。

### 6.2 已给用户的 CMD 二进制比较命令

```cmd
cd /d D:\\Download

echo ===== FILE SIZES =====
dir /-c sweet-qemu-gpu-payload.bin
dir /-c sweet-qemu-gpu-payload-old.bin

echo ===== BINARY DIFFERENCES =====
fc /b sweet-qemu-gpu-payload-old.bin sweet-qemu-gpu-payload.bin > payload-diff.txt

echo ===== FIRST DIFFERENCES =====
more payload-diff.txt
```

当前等待用户回报：
- 两个文件的字节大小；
- `fc /b` 输出开头部分（如输出很长，先给开头即可）。

**当前状态：尚未拿到文件大小或 fc 差异内容。**
不要假设差异只在某个 patch、kernel 或 header；需要等实际比较结果。

### 6.3 当前操作禁令

在差异分析前：
- 不要替换用户正在使用的 payload；
- 不要让用户重启虚拟机；
- 不要让用户刷写/重建镜像；
- 不要擅自 dispatch 新 workflow；
- 不要改仓库源码或补丁。

## 7. 已排除或尚无证据支持的方向

以下内容应谨慎处理：

- **“只启动了一个 CPU”**：现有 ring log 记录 2/2 CPU，当前证据不支持。
- **“Android 整帧复制太慢导致黑屏”**：平均约 1 ms、最大约 3.6 ms，不像主要瓶颈；但其它路径性能仍可能有问题。
- **“系统根本没启动”**：backboardd / SpringBoard 已启动，存在全屏 Metal 绘制与 swap 记录，说明系统已进入 GUI 相关阶段；但不代表 GUI 已成功显示到 Android。
- **“IOMFB present 返回错误就说明完全没显示”**：错误码需要结合 swap sequence 和后续日志判断。
- **“紫色方块就是主要故障”**：不是当前目标；可能来自探针写入。
- **“payload-4cpu-perf6.bin 是用户当前使用文件”**：错误。用户当前使用的是 `sweet-qemu-gpu-payload.bin`。
- **“workflow success 就说明 0098 已应用并进入 payload”**：尚未验证 build log 和 artifact 依赖链。
- **“新旧 payload hash 不同就说明新文件有问题”**：不成立，只说明二进制不相同。

## 8. Codex 接手时的优先工作清单

建议按以下顺序推进，先取证再修改：

### P0：先核对 GitHub 构建链

1. 检查用户仓库 `wangyitao978-blip/vms` 的当前 `main` 和 commit `75aa2dee84c7ee04aaa50fea1e965c5a9eb5f0ba`。
2. 检查 XNU run #36（ID `42513328001`）所有 job logs：
   - 是否 checkout 到上述 commit；
   - 是否复制了 `0098-sweet-fb-copy-diagnostics.patch`；
   - `build.sh` 是否实际应用该补丁；
   - 是否有 patch fuzz / reject / skip / ignored 等信息；
   - 生成的 kernel artifact 的具体文件名、hash。
3. 检查 payload run #11（ID `42513718545`）所有 job logs：
   - 下载了哪个 workflow run 的 `xnu-kernel` artifact；
   - 是否为 run #36 对应的 artifact；
   - payload 的组装命令、嵌入 kernel 的文件路径；
   - 最终 `sweet-qemu-gpu-payload.bin` 的 hash / size。
4. 检查 artifact manifest、build metadata、commit SHA；如果当前 workflow 没有记录这些信息，先不要直接改构建逻辑，先报告缺失证据。
5. 核实用户下载的 zip 确切来自哪个 run，避免 run #8 和 run #11 混淆。

### P1：完成用户本地新旧 payload 对比

等待用户提供：
- 新旧文件大小；
- `fc /b` 差异。

根据差异范围再决定下一步：
- 大小不同：先比较文件头、尾部和结构字段；
- 大小相同且少量偏移不同：定位偏移区间，判断是否为 kernel、patch、metadata 或固定地址内容；
- 大量差异：先确认构建来源和是否整体重编译，不要直接认为内容错误。

Windows 命令保持 CMD 语法。

### P2：沿显示链路定位第一个断点

把显示链路分段，不要混为一谈：

1. SpringBoard / backboardd 是否提交了新 IOSurface；
2. Metal / CA 是否生成新像素；
3. Guest `AppleCLCD::present` 是否执行 framebuffer copy；
4. QEMU `sweet_fb.c` 是否收到 present / descriptor 并更新共享 RAM；
5. Android reader 是否发现 framebuffer 内容变化；
6. `CaCompositor` 是否收到 frame、成功绘制到 Android Surface；
7. Android 最终 UI 是否真正呈现画面。

对每个阶段使用已有日志与源码，不要同时改多个层次。

### P3：针对 display off 记录

ring log 最后一次 display state 为 off，且后续无新 Metal 绘制记录。应检查：
- 是谁触发 display off；
- 是否为 Guest 显示电源管理正常行为；
- 是否存在 display on 之后未恢复绘制的路径；
- 是否与用户观察到的黑屏时间点吻合。

不要仅凭最后一条 off 记录直接强制常亮或改电源管理。先定位调用来源和状态转换。

### P4：只有确认缺陷后再做最小修复

如果确认某个环节有问题：
- 保留原始仓库补丁和构建流程；
- 做最小、可审计的修改；
- 明确说明修改了什么、为什么、影响范围；
- 构建后记录 commit SHA、artifact run ID、文件 SHA-256；
- 与原始产物比较；
- 不擅自删减原始补丁、修改 APK 内容或镜像布局。

## 9. 重要文件 / 符号索引

| 路径 / 名称 | 作用或已知内容 |
|---|---|
| `.github/workflows/xnu.yml` | XNU 云构建工作流 |
| `xnu/patches/0098-sweet-fb-copy-diagnostics.patch` | framebuffer copy 诊断补丁 |
| `vm/metal/SweetMetal.m` | Metal / CA flush / IOMFB present 相关逻辑 |
| `vm/qemu/sweet_fb.c` | QEMU Guest present / framebuffer descriptor / completion |
| `VmEngine.java` | Android framebuffer reader / changed() 抽样 / frame copy |
| `CaCompositor.java` | Android compositor flush / draw / drop |
| `NOTES.md` | 历史测试记录；包含旧的 `payload-4cpu-perf6.bin` 记录 |
| `guest-ring.bin` | 已解析过的 Guest ring log |
| `sweetvm-current.txt` | 已分析过的 Android 侧状态日志 |
| `vmios-host-raster-payload.zip` | 用户提到的最近一次 GPU payload 构建产物 zip |
| `sweet-qemu-gpu-payload.bin` | 用户当前使用的 payload 文件名 |
| `sweet-qemu-gpu-payload-old.bin` | 用户下载用于比较的旧 payload 文件名 |

关键函数 / 日志：
- `AppleCLCD::present`
- `sweet_ca_flush()`
- `sweet_present_surface`
- `sweet_present_pending()`
- `sweet_probe_panel()`
- `VmEngine.changed()`
- `CaCompositor.onFlush()`
- `CaCompositor.onDraw()`
- `SWEET FB COPY:`
- `CAWindowServer serverIfExisting`
- `IOMobileFramebuffer`
- `display off / display on`

## 10. 关键标识与数值速查

- Android package：`dev.vmios.sweetvm`
- Guest framebuffer 全屏尺寸：`1125×2436`
- CPU 初始化：`2/2`
- ring log：319,701 bytes / 5,429 lines / SWEETLOG v1
- 全屏绘制耗时：约 3.5–4.5 秒（历史观测）
- Android 整帧 copy：平均约 1 ms，最大约 3.6 ms（历史观测）
- `IOMobileFramebuffer` present 返回：`0x10000003`
- 诊断探针色块：surface 1 左上角 200×200，`0x00ff00ff`
- 用户仓库：`wangyitao978-blip/vms`
- XNU run #36：`42513328001`
- GPU payload run #11：`42513718545`
- 相关 commit：`75aa2dee84c7ee04aaa50fea1e965c5a9eb5f0ba`
- 新 payload SHA-1：`9b2c563f5cffe6e73e1dd453abd3f96cff9aa0f8`
- 旧 payload SHA-1：`3c39efab2cc62bf37da93e2a8edf2c10fd59f330`

## 11. 建议给 Codex 的开场指令

请先完整阅读本文件，再检查仓库实际状态。严格遵守：
- 当前目标是让 iOS 正常系统界面显示出来；
- 先取证，不要先改代码；
- 不要把用户仓库和上游仓库混淆；
- 不要把旧文件 `payload-4cpu-perf6.bin` 当作当前 payload；
- 不要删除或缩减原始补丁；
- 不要改变 APK / payload / 镜像的原始制作方案；
- 不要擅自 dispatch workflow、替换 payload、刷镜像或要求用户重启；
- 优先核对 run #36 的 XNU 补丁应用记录，以及 run #11 的 payload 下载依赖；
- 需要用户执行命令时，统一给 Windows CMD 命令；
- 对每条结论注明证据来源和置信度；
- 如果本文与仓库当前源码 / workflow 实际状态冲突，以仓库实际内容为准，并报告差异，不要静默覆盖本文记录。

**当前暂停点：** 用户已经提供新旧 payload SHA-1，已收到比较命令，但尚未提供两个文件的大小和 `fc /b` 输出。等待该结果，同时可独立审计 GitHub Actions run #36 / #11 的构建链；不要先替换 payload。
