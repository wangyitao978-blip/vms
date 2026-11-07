# VMS Android build

This repository contains the Android project copied from [LQS686/vmios](https://github.com/LQS686/vmios/tree/main/android).

Run the **Copy Android source and build APK** workflow from the Actions tab to import the upstream `android/` directory and build a debug APK.

The Android source directory alone does not include all QEMU engine binaries, Termux libraries, or demo payload assets required for a fully functional runtime. See `android/README.md` and the upstream project documentation for those additional requirements. Preserve the upstream MIT license and third-party notices.