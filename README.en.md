# Komiho

<div align="center">

![Komiho](.github/readme-images/app-icon.png)

**A native Android manga reader**

Komga · local files · WebDAV · SMB — one UI for every comic source

`cn.ruzhe.komiho` ｜ `1.5.1-beta (15)` ｜ Android 8.0+

[![Download](https://img.shields.io/badge/Download-Release-2ea043?style=for-the-badge)](https://github.com/a245888295-dot/Komiho/releases/latest)
[![Changelog](https://img.shields.io/badge/Changelog-v1.5.1--beta-blue?style=for-the-badge)](./CHANGELOG.en.md)

[English](./README.en.md) | [中文](./README.md)

</div>

---

## ⬇️ Download

> **This is a Beta build**
> `v1.5.1-beta` is a test build whose features and stability are still being validated.
> It will be folded into a stable release once verified. Please report issues via
> "About → Feedback & suggestions".

**→ [Download the latest APK from Releases](https://github.com/a245888295-dot/Komiho/releases/latest)**

1. Download `komiho-*.apk` (`arm64-v8a`, covers almost all devices)
2. Allow "install unknown apps" and install
3. Pick a comic source on first launch

> Image enhancement (NPU) additionally needs the model package: `komiho-model-v81.apk`
> from the same release page (Qualcomm SoCs only).

---

## 🆕 What's new in v1.5.1-beta

- **No more black screens when paging fast** — pages render as the original image immediately; 0.6 s after paging stops the current page is re-enhanced and the following pages are prewarmed (Settings → Image → can be disabled)
- **Preload page count now really works** — the prepared-result cache ceiling is raised from 4 to 12 pages, so the configured depth is actually preloaded and cached
- **Memory-budget eviction** — enhanced bitmaps are only evicted once the total exceeds 768 MB, instead of a hard page-count cap
- **Prewarm jobs are no longer wasted** — turning a page only drops targets that have drifted out of the prefetch window

See [CHANGELOG.en.md](./CHANGELOG.en.md) for the full list.

---

## 🕘 Release history

| Version | Highlights |
|---|---|
| **v1.5.1-beta** | Skip enhancement while paging fast (re-enhance after settling), preload depth now really takes effect (12 pages), memory-budget cache eviction |
| **v1.5.0** | E-Ink mode, MOBI / AZW3 / AZW support, parchment / kraft reading backgrounds, deep crop by connected-component bounding box, feedback entry |
| **v1.4.1** | WebDAV sync, moiré reduction, progress-bar thumbnails, diagnostic log export, Real-CUGAN Pro/SE NPU models, PDF covers and page counts |
| **v1.3.1** | Force-AI-enhance for large images, badge showing output resolution, "opening" indicator, remote-cache progress animation |
| **v1.3.0** | PDF reading over local / WebDAV / SMB, file-browser search, denoise, natural sorting |
| **v1.2.0** | AI super-resolution (GPU / NPU), source manager and startup region, loose-image folders with auto-continue, tunable read caches, Komga downloads |
| **v1.1.0** | Local / WebDAV / SMB browsing, bookmarks and history, unified source management |
| **v1.0.6** | First release: multi-server Komga, library / series / book browsing, full reader |

---

## 🔧 Changes on this branch (after v1.5.0)

These were developed on this branch after the v1.5.0 release:

### Reading and enhancement scheduling

- **Skip enhancement while paging fast** — pages render as the original image immediately; 0.6 s after paging stops the current page is re-enhanced and the following pages are prewarmed
- **"Enhancing…" overlay** — no more half-enhanced frames while the engine works
- **Collapsible enhancement badge** — per page it shows the engine, elapsed time and the exact skip reason (e.g. "waiting to retry" when preempted)
- **No CPU fallback in AI modes** — an NPU failure now keeps the original image and retries, instead of silently degrading to CPU
- **Force-AI-enhance for large images** wired through both gates and enabled by default
- Fixed "page never refreshes after falling back to CPU super-resolution"

### Models and settings

- **Model selection rebuilt as Komikku's four axes**: family / denoise level / scale / precision
- Aligned with three Komikku settings: max processing resolution, max resolution (skip above it), preload pages
- **AI scale now follows the selected model's scale**, supporting 3x models

### Preload and cache

- **Preload depth now really takes effect**: cache ceiling raised from 4 to 12 pages
- **Memory-budget cache**: enhanced bitmaps are evicted only once the total exceeds 768 MB
- **Paging no longer cancels queued prewarm jobs** — only targets that drifted out of the prefetch window are dropped

### Build

- The model-package workflow now reuses the host keystore cache, fixing "Tag number over 30"

---

## ✨ Features

### 📚 Multi-source reading

Komiho unifies every source into one browsing and reading experience:

* **Komga** — connect to your self-hosted Komga; browse libraries, series and books, with multi-server, multi-library and reading-progress sync
* **Local files** — browse and read comics stored on the device
* **WebDAV** — connect to a NAS or any WebDAV server; browse and read remotely without downloading whole books
* **SMB / CIFS** — access LAN shares and read network comics directly

### 📦 Supported formats

* **Direct read (local / WebDAV / SMB)**: `CBZ` `CBR` `CB7` `CBT` `ZIP` `RAR` `7Z` `TAR` `PDF` `EPUB` `MOBI` `AZW3` `AZW`, plus **loose-image folders** (just put images in a directory)
* **Via Komga**: page rendering is handled server-side, supporting everything Komga supports (except EPUB)

### 📖 Reading experience

Built on the MihonSY reader and continuously tuned for real use:

* Horizontal / vertical / webtoon modes
* LTR / RTL, single / dual page
* Pinch-zoom and gestures, adjustable animations and scrolling
* Reading-progress memory, history and bookmarks
* **E-Ink mode**: Settings → Appearance, dedicated grayscale rendering for e-ink screens
* Reading backgrounds: solid / parchment / kraft

### 🖼️ Image enhancement (super-resolution)

Built-in enhancement on **CPU / GPU / NPU**, tuned for manga line art and balancing quality, speed and memory.

* **NPU** (Qualcomm QNN/HTP, requires the model package): fastest, measured around 0.9 s/page
* **GPU** (NCNN Vulkan): broad compatibility
* **CPU**: fallback

> Notes:
> * Enhancement runs at the decode stage; the first GPU/NPU launch waits for engine init
> * `AnimeVideo` suits colour comics, `Omni-Mini` suits black-and-white manga, `Omni-Turbo` is stronger but slower
> * GPU tile size affects throughput; 192 is recommended
> * NPU models may show slight colour shift — a speed/quality trade-off, not a bug

### 🖥️ Phones, tablets and large screens

Adaptive layouts for phones, tablets and large screens, with bottom / left / right navigation.

---

## ⚠️ Notes

* The Komga feature requires **your own Komga server**
* Komiho ships no comic content; please respect copyright

---

## 👤 About

This is a **personal, self-use** branch: not an official project, not representing any upstream
position, and with no commercial plans.

Most changes come from everyday use — whatever makes reading more comfortable, without a grand
roadmap. If it happens to suit you too, feel free to use it; if not, no hard feelings.

* **Author**: [a245888295-dot](https://github.com/a245888295-dot)
* **AI collaboration**: DeepSeek V4.1 (code and documentation)

> **Disclaimer**: this app does not produce manga, it just carries it. All content comes from your own Komga / NAS / local files; copyright belongs to the original authors.

Feedback is welcome via "About → Feedback & suggestions".

---

## 🙏 Acknowledgements

Komiho stands on the shoulders of these open-source projects — without them it would not exist:

* [Mihon](https://github.com/mihonapp/mihon) / [Tachiyomi](https://github.com/tachiyomiorg) — the reader architecture this app is built on
* **MihonSY (the SY branch)** — the direct upstream of this branch; the reader core and many reading-experience improvements come from it
* [Komikku](https://github.com/komikku-app/komikku) — reference for enhancement scheduling, the four-axis model UI and related settings
* [ncnn](https://github.com/Tencent/ncnn), [waifu2x](https://github.com/nagadomi/waifu2x), [Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN), [Real-CUGAN](https://github.com/nihui/realcugan-ncnn-vulkan) — super-resolution runtimes and models
* [Coil](https://github.com/coil-kt/coil), [Subsampling Scale Image View](https://github.com/davemorrissey/subsampling-scale-image-view) and other Android libraries
* And everyone who contributed code, reported issues or wrote documentation

**Thanks to open source, and to everyone who makes these tools exist.**

---

## 📝 Changelog

See [CHANGELOG.en.md](./CHANGELOG.en.md) (and [中文](./CHANGELOG.md)).
