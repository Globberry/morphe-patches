# 👋🧩 Morphe Patches template

Template repository for Morphe Patches.

## ❓ About

Patches for apps I like.

<!-- TODO: Update this about section with a brief introduction/summary about this repo and what it offers. -->

### How to use these patches

Click the button below, or manually add this repo url to Morphe Manager: https://github.com/Globberry/keyflow-patches

<a href="https://morphe.software/add-source?github=Globberry/keyflow-patches">
  <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
</a>

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.7.3](https://github.com/Globberry/morphe-patches/releases/tag/v1.7.3)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;6 patches total
<details open>
<summary>📦 Keyflow&nbsp;&nbsp;•&nbsp;&nbsp;6 patches</summary>
<br>

**Package name:** `com.angkin.keyflow`

**🎯 Supported versions:**

| 1.5.10 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [PairIP License Check](#pairip-license-check) | Neuters PairIP license verification so the app runs without a valid license. |  |
| [Pro Unlock](#pro-unlock) | Forces all Pro features enabled, keeps Pro active after signer checks, and unlocks Pro exports (including the paid-export receipt check). |  |
| [Signer Identity](#signer-identity) | Forces the app's signer identity, signature checks, and signer-identity string to match the original publisher certificate. |  |
| [[Export] 12 Purchase Allow](#export-12-purchase-allow) | Always accept the paid-export purchase check (LP0JcGi9.MeOIJuj). |  |
| [[Export] 8 Diagnostics](#export-8-diagnostics) | Log the whole export flow to logcat (tag KFPro). Read with: adb logcat -s KFPro |  |
| [[Export] 9 Toast](#export-9-toast) | Show a toast naming the reason the export was cancelled. |  |

</details>

<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## 📜 License

UserXYZ Patches are licensed under the [GNU General Public License v3.0](LICENSE)
