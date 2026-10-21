# Komiho

<div align="center">

![Komiho](.github/readme-images/app-icon.png)

**原生 Android 漫画阅读器**

Komga · 本地文件 · WebDAV · SMB，一套界面读完所有漫画

`cn.ruzhe.komiho` ｜ `1.5.1-beta (15)` ｜ Android 8.0+

[![下载](https://img.shields.io/badge/下载-Release-2ea043?style=for-the-badge)](https://github.com/a245888295-dot/Komiho/releases/latest)
[![更新记录](https://img.shields.io/badge/更新记录-v1.5.1--beta-blue?style=for-the-badge)](./CHANGELOG.md)

[中文](./README.md) | [English](./README.en.md)

</div>

---

## ⬇️ 下载

> **当前为测试版（Beta）**
> `v1.5.1-beta` 是正在验证的测试包，功能与稳定性仍在观察中。
> 稳定后会归入正式版本。遇到问题欢迎通过「关于 → 问题建议反馈」提交。

**→ [前往 Releases 下载最新 APK](https://github.com/a245888295-dot/Komiho/releases/latest)**

1. 下载 `komiho-*.apk`（`arm64-v8a`，绝大多数设备适用）
2. 允许「安装未知应用」，点击安装
3. 首次打开按提示选择漫画来源

> 图像增强（NPU）需要额外安装模型包：Release 页面里的 `komiho-model-v81.apk`（仅高通 SoC）。

---

## 🆕 本版更新（v1.5.1-beta）

- **快速翻页不再黑屏** —— 翻页瞬间直接出原图，停下 0.6 秒后自动补超分，并预热后面几页（设置 → 图像 → 可关闭）
- **预加载页数真正生效** —— 预处理缓存上限从 4 页放开到 12 页，设几页就预热/缓存几页
- **内存预算制** —— 增强位图总量超过 768MB 才淘汰最旧页，不再按固定页数一刀切
- **预热不再白跑** —— 翻页不再作废排队中的预热任务，只放弃已跑出预取窗口的目标

完整记录见 [CHANGELOG.md](./CHANGELOG.md)。

---

## ✨ 特性

### 📚 多来源阅读

Komiho 把不同来源的漫画统一到同一套浏览与阅读体验：

* **Komga** — 连接自托管 Komga，浏览书库、系列和漫画，支持多服务器、多书库与阅读进度同步
* **本地文件** — 直接浏览和阅读设备上的漫画文件
* **WebDAV** — 连接 NAS 等 WebDAV 服务，在线浏览并直接阅读远程漫画，无需整本下载
* **SMB / CIFS** — 访问局域网共享文件夹，直接阅读网络漫画

### 📦 支持的格式

* **本地 / WebDAV / SMB 直读**：`CBZ` `CBR` `CB7` `CBT` `ZIP` `RAR` `7Z` `TAR` `PDF` `EPUB` `MOBI` `AZW3` `AZW`，以及**散图文件夹**（目录内直接放图片即可）
* **通过 Komga 服务器**：由服务端负责转页，支持 Komga 所支持的全部格式（除 EPUB）

### 📖 阅读体验

基于 MihonSY 的阅读器，并针对实际使用持续优化：

* 横向翻页 / 垂直翻页 / 条漫
* LTR / RTL，单页 / 双页
* 缩放与手势操作，可调节动画与滚动行为
* 阅读进度记忆、历史记录与书签
* **E-Ink 模式**：设置 → 外观，专为墨水屏切换灰度显示
* 阅读背景：纯色 / 羊皮纸 / 牛皮纸

### 🖼️ 图像增强（超分）

内置基于 **CPU / GPU / NPU** 的图像增强，针对漫画线条优化，在画质、速度与内存之间取平衡。

* **NPU**（高通 QNN/HTP，需安装配套模型包）：速度最快，实测约 0.9s/页
* **GPU**（NCNN Vulkan）：兼容性好
* **CPU**：兜底

> 说明：
> * 增强基于解码端，GPU/NPU 首次打开需等待引擎初始化
> * `AnimeVideo` 更适合彩漫，`Omni-Mini` 更适合黑白漫，`Omni-Turbo` 性能更强但耗时更长
> * GPU 分块大小影响处理速度，建议 192
> * NPU 模型可能出现轻微偏色，是速度与性能的取舍，并非 BUG

### 🖥️ 平板与大屏

针对手机、平板和大屏设备提供自适应布局，支持底部 / 左侧 / 右侧导航。

---

## ⚠️ 注意

* Komga 功能需要连接你自己的 **Komga 服务器**
* Komiho 不提供任何漫画内容，请遵守相关版权规定

---

## 👤 关于

* **作者**：[a245888295-dot](https://github.com/a245888295-dot)
* **AI 协作**：DeepSeek V4.1

本项目为测试阶段的独立分支，尚未正式开源对外协作；反馈与建议欢迎通过「关于 → 问题建议反馈」提交。

---

## 📝 更新记录

详见 [CHANGELOG.md](./CHANGELOG.md)（[English](./CHANGELOG.en.md)）。
