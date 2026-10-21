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

* **Author**: [a245888295-dot](https://github.com/a245888295-dot)
* **AI collaboration**: DeepSeek V4.1

This is an independent branch in its testing phase and is not yet open for external contribution.
Feedback is welcome via "About → Feedback & suggestions".

---

## 📝 Changelog

See [CHANGELOG.en.md](./CHANGELOG.en.md) (and [中文](./CHANGELOG.md)).
