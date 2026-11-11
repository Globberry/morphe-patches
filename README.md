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
> **[v1.4.0](https://github.com/Globberry/morphe-patches/releases/tag/v1.4.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;20 patches total
<details open>
<summary>📦 Keyflow&nbsp;&nbsp;•&nbsp;&nbsp;20 patches</summary>
<br>

**Package name:** `com.angkin.keyflow`

**🎯 Supported versions:**

| 1.5.10 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Keyflow Export Pro Gate Bypass](#keyflow-export-pro-gate-bypass) | Allows exporting projects that use Pro features by bypassing the Pro gate in the export pipeline. |  |
| [Keyflow Trusted Signer Bypass](#keyflow-trusted-signer-bypass) | Always report the app signature as trusted so Pro is not reset when opening a project or exporting. |  |
| [Keyflow byYKY0yXO Signer Bypass](#keyflow-byyky0yxo-signer-bypass) | Return a dummy signer identity string during export so the export path does not fail on re-signed APKs. |  |
| [Keyflow f6olX Goto Fix](#keyflow-f6olx-goto-fix) | Replaces failure goto sites in f6olX with nop so all code paths reach Premium (Keyflow 1.5.10 export). |  |
| [PairIP License Check](#pairip-license-check) | Neuters PairIP license verification so the app runs without a valid license. |  |
| [Pro Unlock](#pro-unlock) | Forces all Pro features enabled and replaces the Google Play confirmation with a fake success, so ad surfaces never appear. |  |
| [Signer Identity](#signer-identity) | Forces the app's signer identity to match the original publisher certificate. |  |
| [[ALL] PairIP License Check](#all-pairip-license-check) | Neuters PairIP license verification using fingerprint matching (version-robust). |  |
| [[ALL] Pro Unlock](#all-pro-unlock) | Forces all Pro features enabled using fingerprint matching (version-robust for method name changes). |  |
| [[ALL] Signer Identity](#all-signer-identity) | Forces the app's signer identity to match the original publisher certificate (fingerprint-based, version-robust). |  |
| [[Export] 1 kts6dAU4 Gate](#export-1-kts6dau4-gate) | Always allow Pro features in the export Pro gate (Lkts6dAU4.NMGZ06). |  |
| [[Export] 10 LP4eSlPPu Allow](#export-10-lp4eslppu-allow) | Always allow an individual Pro feature (LP4eSlPPu.invoke). |  |
| [[Export] 2 kts6dAU4 Throw](#export-2-kts6dau4-throw) | Do not throw the Pro-required exception (LwNKvkk7) during export. |  |
| [[Export] 3 y88yfJ73C Allow](#export-3-y88yfj73c-allow) | Always let the export pass the LCtCUGg.y88yfJ73C abort check. |  |
| [[Export] 4 ZFJe6vx50 Skip](#export-4-zfje6vx50-skip) | Never take the export-abort branch in LZFJe6vx50.invoke. |  |
| [[Export] 5 kCg72Z4t State](#export-5-kcg72z4t-state) | Publish PLAYING instead of PRO_REQUIRED/ERROR as export state. |  |
| [[Export] 6 LCWcMAXf Error](#export-6-lcwcmaxf-error) | Pass a null error message into the pre-export step. |  |
| [[Export] 7 LsvcJNUbK3 Allow](#export-7-lsvcjnubk3-allow) | Always allow the LsvcJNUbK3 sub-check of the export Pro gate. |  |
| [[Export] 8 Diagnostics](#export-8-diagnostics) | Log every export Pro gate decision to logcat (tag KFPro). Read with: adb logcat -s KFPro |  |
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
