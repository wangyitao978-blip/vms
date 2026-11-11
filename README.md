# VMs

**VMs 是一个面向 Android 的实验性 iOS 虚拟机项目。**

项目目标是在 Android 设备上通过 QEMU 运行真实的 iOS 16 系统环境，而不是制作一个仿 iOS 桌面，也不是把 iOS 应用转换成 Android 应用。项目围绕 QEMU、经过适配的 XNU 内核、SWEETXNU 启动 payload、虚拟存储和显示设备，以及宿主侧渲染组件展开。

> **项目状态：实验性开发中。** 能启动内核和真实 iOS 用户态，不等于完整 iOS 桌面已经稳定可用。显示合成、图形兼容性、性能和不同设备适配仍在持续开发与验证。

## 它是什么

VMs 将以下部分组合成一套虚拟设备：

- **QEMU**：在 Android 上提供 ARM64 虚拟机和 TCG CPU 模拟。
- **XNU / SWEETXNU**：为 iOS 用户态提供经过适配的 Darwin/XNU 内核。
- **iOS 系统环境**：目标为 Apple iOS 16.7.16（20H392，iPhone X / iPhone10,3 / D22AP）的真实系统用户态。
- **虚拟硬件**：包括 UFS 存储、共享内存 framebuffer、触摸输入和宿主通信接口。
- **SweetMetal 与宿主光栅器**：尝试承接客户机 CoreAnimation/Metal 渲染工作，并把图形结果呈现到 Android 屏幕。

## 能做什么

当前代码与已有构建支持或正在实现的能力：

- 在 QEMU TCG 中启动适配过的 XNU，并运行真实 iOS 用户态组件。
- 使用独立的 iOS 根镜像文件作为虚拟机存储；根镜像由使用者自行准备。
- 通过共享内存 framebuffer 传递客户机画面，并接收触摸输入。
- 使用 SweetMetal、宿主侧软件光栅路径及 Android JNI 光栅器处理部分图形命令。
- 在支持的构建配置中使用多线程 TCG、多个虚拟 CPU，以及可选的原生 framebuffer present 路径。
- 通过 Android APK 管理虚拟机启动、运行状态和相关选项。

上述能力的完成度并不相同。某条代码路径已经实现或 APK 已经构建成功，不代表它已在所有设备上完成端到端验证。

## 不能做什么 / 当前限制

- **不是完整的 iOS 兼容层**：不能保证任意 iOS 版本、任意 iPhone 型号或任意 iOS App 都能运行。
- **不是 GPU 直通**：QEMU TCG 虚拟机并没有把 Android 手机上的 Adreno GPU 直接交给 iOS。宿主图形路径属于项目自建的协议与渲染实现，功能和性能仍有限。
- **不能保证桌面显示完整**：内核启动、SpringBoard 进程启动和窗口树创建，不等于 WindowServer/CoreAnimation 已经把完整桌面稳定呈现在屏幕上。
- **不能保证流畅度或兼容性**：不同 SoC、Android 版本、内存容量和驱动环境可能产生明显差异。
- **不附带 Apple 系统镜像**：仓库不分发 iOS IPSW、根文件系统、共享缓存或其他 Apple 专有系统文件。使用者必须自行合法准备所需文件。
- **不能替代实体 iPhone**：蜂窝基带、真实 Apple 安全硬件、摄像头、Face ID、传感器及其他专用硬件并未被完整虚拟化。
- **不是面向日常使用的稳定产品**：目前更适合开发、调试和研究，不应把实验构建视为可用于生产或数据安全关键场景的软件。

## 我们已经做了什么

项目迭代包含以下工作（不同项目分支和构建的状态请以对应提交与 GitHub Actions 记录为准）：

1. **内核适配**：围绕 XNU 8796 增加和维护虚拟设备、启动、存储、显示、输入及相关系统服务所需的补丁。
2. **虚拟存储**：实现 QEMU 侧 UFS 设备路径，使客户机可以使用外部准备的 iOS 根镜像。
3. **显示与输入**：建立共享 framebuffer、触摸输入和宿主/客户机之间的通信路径。
4. **SweetMetal**：实现虚拟 Metal 设备及渲染命令转发的实验性路径，并持续补齐宿主侧光栅处理。
5. **Android 宿主端**：开发 QEMU 启动管理、JNI 光栅器、帧缓冲呈现、CPU/内存管理和诊断日志。
6. **性能与稳定性工作**：调整虚拟 CPU 配置、TCG 线程亲和性、内存预分配条件、日志限流和 framebuffer present 流程。
7. **可回退的实验选项**：原生 present 路径提供开关，便于在设备出现兼容性问题时回退到其他呈现方式。

### 当前 APK / Payload 说明

Android 应用显示名称为 **VMs**。APK、Host Raster payload 和 XNU 内核分别由不同 GitHub Actions 工作流构建，不能把其中一个构建成功误认为另外两个也已完成。

**当前仓库状态说明（2026-11-12）：** APK 工作流尚未加入 Host Raster payload 的自动注入步骤，APK 也尚未包含 `BootAssets` 自动释放实现。因此目前不能宣称安装 APK 后就会自动释放内置 payload；请以最新 APK 构建说明和实际 artifact 内容为准。计划内置时只选择 **Host Raster payload**，不选择 Guest Local payload。XNU 沿用已验证的既有构建，不因 APK 图标或文档修改而重新构建。

## 仓库结构

| 路径 | 内容 |
| --- | --- |
| `android/` | Android 应用、QEMU 启动管理和宿主侧代码 |
| `vm/` | 虚拟硬件、显示通信及 SweetMetal 实现 |
| `xnu/patches/` | 按编号应用的 XNU 内核补丁 |
| `tools/` | ADT、payload、镜像和调试工具 |
| `shim/` | 早期裸机移植实验，当前暂停维护 |
| `docs/` | 设计说明、分析记录和项目约定 |
| `NOTES.md`、`FIX.md` | 开发交接、修复与构建记录 |

## 构建与使用

Android APK 可在项目提供的 Android Gradle 构建环境中编译：

```sh
cd android
./gradlew assembleDebug
```

XNU 与 payload 使用各自的 GitHub Actions 工作流构建。具体命令、依赖和产物名称可能随分支调整，请以相应 workflow 和构建记录为准。

运行前需要准备与当前 payload 匹配的 iOS 根镜像及启动文件。请先阅读项目文档和对应构建记录，并保留原始镜像与可回退的旧构建。

## 许可证与致谢

项目代码许可证见 [LICENSE](LICENSE)。Apple 的 iOS 系统文件及相关商标归其各自权利人所有；本仓库不提供 Apple 专有系统镜像。

项目依赖和致谢见 [CREDITS.md](CREDITS.md)。本项目是独立的实验性工程，与 Apple Inc. 无隶属或认可关系。
