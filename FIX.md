# FIX — vmios change/build ledger

> Hard limit: 900 KB. Read before every repository operation. Never rename/delete user-owned branches.

## Standing requirements
- Keep branch names `apk`, `payload`, `xun`; never rename/delete.
- Only make requested changes. Record changes, reversals, builds, run IDs, artifacts, and user GitHub instructions.
- CI success is not device validation.
- 550/551 are evidence-probe only; do not change decode mapping. 554/555 unchanged.
- Keep below 900 KB.

## 2026-11-11 handoff
Previous diagnostic work exists only on `fix/mobilegestalt-boot-and-side-controls`:
- `vm/metal/SweetMetal.m` probe commit `0c79ec1034256c7a9621730ad073dfede47f4ef1`: mapped-only 64x64 fmt550 probe, stride/readable-extent guarded, first 48 raw words and top-two-bit counts. No decode mapping changes.
- `xnu/patches/0097-sweet-gpu-ca-draw.patch` diagnostic allocation-failure log commit `5ec6cf3c8e8603635ba548f6c503588c9245ee0f`; not proof allocation is fixed.
- Do not blindly merge the temporary branch. Inspect and selectively port only relevant changes.
- Previous temporary-branch builds: APK run `42897966590`; XNU run `42897881292`; Payload run `42897545726`. The payload used stale XNU run `42853907291`; do not treat it as a valid matching build.

## Current branch workflow correction
User instructed: restart and fix the workflow/branch mismatch. Intended build branches:
- APK workflow must listen to `apk`.
- Payload workflow must listen to `payload`; manual dispatch must require an explicit XNU run ID to prevent stale default kernel.
- XNU workflow must listen to `xun`.
No source code has yet been ported in this branch. Record actual commits and runs below.


## Workflow correction commits
- APK: updated `.github/workflows/build-apk.yml` so push builds listen only to `apk`; commit `4e232165d79121a97fe786598655996ac92ccbe2`. Build not yet verified.

- Branch build triggered by workflow correction: APK run `42900316057` (head `4e232165d79121a97fe786598655996ac92ccbe2`) and follow-up ledger push run `42900327731` (head `f6ac81c0d708b0d1e333de59eb6642c3b1ada9df`); both were in progress at verification. URLs: https://github.com/wangyitao978-blip/vms/actions/runs/42900316057 and https://github.com/wangyitao978-blip/vms/actions/runs/42900327731.


## 2026-11-11 A1 offscreen IOSurface routing
- Review result: A1's core design is valid, but the repository already had IOSurface allocation/lifetime support on `apk`; the missing path was host routing of an owned internal render-target IOSurface. Do not duplicate the allocator.
- APK source fix: `vm/metal/SweetMetal.m` commit `4d3406fef4fa7a6c6c8071c3e400a4af858424d6`. Host routing now treats either a cross-process mapped IOSurface or an owned internal render-target IOSurface (`ownsRenderSurface`) as directly mappable and sends its surface ID to `sweet_ca_map`. The 1 MiB ceiling remains only for heap-backed fallback uploads; it no longer blocks IOSurface-backed textures. Actual IOSurface bytesPerRow is preserved.
- Payload review: `android/app/src/main/java/dev/vmios/sweetvm/CaCompositor.java` already avoids reusing decoded pixels for `F_INTERNAL` surfaces and reuses the output array, so no Java change was needed on `payload`.
- Wired budget: IOSurface lifetime is released by `SweetTexture::releaseRenderSurface`/dealloc. No LRU eviction was added: evicting a live CA-owned render target would invalidate an active texture. The 1536 MiB cap and allocation-failure heap/local-raster fallback remain. The repository carries only a patch for `SweetSurface.cpp`, not the full `SweetSurface::free` implementation, so the kernel-side wired-counter decrement and the `allocSize > 0 => memory != NULL` invariant cannot be independently certified from this checkout; they must be verified against the XNU build source before claiming the budget prerequisite complete.
- Scope correction: exploratory Metal/Java edits made on `xun` during this pass were reverted because those files belong on their APK/Payload build branches. Reverts: `b3c3d7bfdf48ceb55a0b213d3c261a0dcf2aa7c9` and `a2a9e8cc40a65ea9080a334caed29c192ec2ac98`. The XNU patch was not changed in this A1 pass.
- Build/device validation: not yet verified. The APK source commit is committed; do not treat it as a successful APK build or device test. No new XNU/Payload build is claimed.


## 2026-11-11 offscreen decode/cache hotfix
- User-provided ring5/ring6 analysis identified repeated per-draw decode of F_INTERNAL render targets. Verified on current `payload` source: `CaCompositor.texture()` bypassed cache whenever `internal` was true. Hotfix restores same-frame reuse for all non-upload surface textures; existing `onFlush()` eviction removes all non-upload decoded entries at the frame boundary, so internal entries cannot survive into the next frame. Added explicit `Decoded.internal` metadata and flush eviction condition.
- APK branch: `SweetMetal.m` now creates IOSurface render targets only at >=256 KiB (width*height*4); smaller targets retain calloc/local-raster behavior. The two per-target IOSurface creation/render-target log sites are each capped at 32 messages. No decoder format mapping or XNU patch was changed.
- Verification required on device: per-frame `decodes=` should fall, frame `ms=` and host flush latency should improve; verify small targets no longer emit IOSurface creation logs. This is a code hotfix, not device validation.


## 2026-11-11 APK-owned compositor cache fix
- Build ownership check: `build-apk.yml` compiles the Android Java compositor; the Payload workflow separately compiles `vm/metal/SweetMetal.m`. Applied the same-frame `F_INTERNAL` decoded-pixel cache fix to the APK branch's `CaCompositor.java` as well, so the APK actually contains the Java hotfix. `Decoded.internal` is set at decode time and internal entries are explicitly invalidated at `onFlush()`; all non-upload surface entries continue to be evicted at the frame boundary.
- This closes the branch placement gap: APK carries the Android compositor change; Payload carries the Metal plugin threshold/log-throttle change and matching compositor source mirror. No XNU patch changed.


## 2026-11-12 final ring12 APK pass
- APK branch only; `main` and `xun` unchanged. `CaCompositor` reads `reserved` at byte offset 84 as `texFn` and passes it to both Java raster fallback and JNI `nativeTriangle`. `sweetraster.c` applies alpha-only texture multiplication for texFn 18/48, otherwise preserves RGBA multiplication.
- `BootConfig.offload` defaults true and persists; Advanced options exposes `原生 present（零拷贝，出问题可关）`. `iosCmdline()` appends `sweetfb_offload=1` after `tcgmulti` unless already specified; disabling the checkbox forces `sweetfb_offload=0`. Existing QEMU sweet-fb device/completion handling is retained.
- `VmEngine` taskset mask changed from `fc` to `e0` (host CPUs 5–7). Cold-boot preallocation now uses available memory >=4 GiB. `MainActivity` enforces `cpus=3`; 2-second `tlto_us/slto_us` are preserved.
- APK workflow auto-builds on `apk` branch push. No XNU change. CI success is not device validation; verify `present=native`, completion/copies growth, and 3 PSCI CPUs on device.


## 2026-11-12 VMs branding and README
- Updated the APK launcher artwork to use the user's supplied black-and-white image in the launcher PNG resources and adaptive-icon foreground. Kept both launcher resource names (regular/round) consistent.
- Verified AndroidManifest still uses @string/app_name and strings.xml already sets app_name to "VMs"; no package/applicationId rename was made, preserving installed app data compatibility.
- Replaced README with a project introduction covering purpose, architecture, current capabilities, limitations, completed engineering work, and the explicit APK/payload status.
- At the time of the branding commit, APK did not yet bundle a payload. The subsequent bundling implementation is recorded below.
- No changes to main or xun. No XNU build triggered.


## 2026-11-12 APK bundled Host Raster payload
- Source payload: successful Payload run `43079641260`, branch `payload`, source commit `3547230786df69f26dd0af59280f43e392b83cfc`; artifact `vmios-host-raster-payload` ID `13882834913` (12,574,633 bytes). The APK workflow validates run status, branch, and workflow path before downloading it. It downloads only `vmios-host-raster-payload`; Guest Local is never requested or packaged.
- APK workflow adds a pre-build injection step. It verifies the payload against the artifact's `SHA256SUMS`, copies only `sweet-qemu-gpu-payload.bin` into `android/app/src/main/assets/boot/`, and writes `payload.sha256`. Post-build verification checks both asset paths and recomputes the embedded payload SHA-256 from the APK.
- Added `BootAssets.java`: first launch extracts the asset to `files/vm/sweet-qemu-gpu-payload.bin` through a temporary file, verifies SHA-256, syncs, atomically renames, and records app version + payload hash in `payload.ver`. Later launches reuse it only when marker and actual file hash match.
- MainActivity starts extraction in a background thread and switches the default payload path to the bundled Host Raster file. Starting iOS is blocked until the bundled file exists; custom/old payload paths are not used for the default boot. User-supplied root image remains external/imported.
- Removed `bin` from Android `noCompress` configuration so the payload remains compressed in the APK; no legacy `aaptOptions { noCompress "bin" }` was added.
- Build must be checked against the final APK branch head after this ledger update. CI artifact success is not device validation; on device verify `files/vm/sweet-qemu-gpu-payload.bin` and `files/vm/payload.ver` after first launch.
- User instruction: do not ask for another “continue”; carry out the bundling and report only after the APK build and artifact verification complete.


## 2026-11-12 Host Raster payload bundled APK build verified
- APK source commit with working extraction: `2e6e509a8c15de08278b8f37568dc301b531ecbb`. Final successful APK workflow run: `43084051387`, branch `apk`, conclusion `success`; artifact `vmios-debug-apk-with-engine` ID `13886195524`, size 56,799,786 bytes. APK SHA-256: `bee0238250de0893f7ded540ee5f91fc266dd262f8fbe7edd858900c7492a994`.
- CI explicitly completed the Host Raster injection step and post-build APK verification. Both `assets/boot/sweet-qemu-gpu-payload.bin` and `assets/boot/payload.sha256` were found inside the APK; extracted payload SHA-256 matched `7204f83ba7a8370f1134915fc2f47516e66366e038c2d6f62bb06d5b584c4e8d`. Only artifact/run `43079641260` Host Raster was downloaded; Guest Local was not requested or included.
- `BootAssets.ensure` compiled successfully after replacing unavailable generated `BuildConfig.VERSION_CODE` with PackageManager version metadata and correcting the Java line break. First-run extraction and hash validation are implemented; actual device extraction remains untested until installed.
- CI path filters now limit automatic APK builds to Android source/assets and the APK workflow; FIX/NOTES/README-only updates do not trigger redundant builds. Workflow commit `18083dc84949cd3a3166e6106bbd27b8bfc080d3`; its validation build run is being checked separately.
- Device verification is still required: after first launch, check `files/vm/sweet-qemu-gpu-payload.bin` and `files/vm/payload.ver` with `run-as`. CI success does not prove on-device extraction or boot.

- Final workflow-trigger-filter validation build: run `43084384868` (head `18083dc84949cd3a3166e6106bbd27b8bfc080d3`) also completed successfully; artifact `vmios-debug-apk-with-engine` ID `13884644789`, 56,799,787 bytes. APK SHA-256 `92596235e296278653f1f262d60e935b2cff91e01b060edc3140c02834b8ebec`; embedded Host Raster payload SHA-256 again matched `7204f83ba7a8370f1134915fc2f47516e66366e038c2d6f62bb06d5b584c4e8d`. This is the final APK artifact after workflow trigger filtering.


## 2026-11-12 A7-A10 unified main-screen refactor
- Replaced the old scrolling `showHome()` page with a single code-built VMs main screen shared by idle and running states. The main screen contains the host metrics strip, central framebuffer, side placeholders, and 11U bottom control bar. Old import/advanced/log/snapshot flows remain reachable through existing handlers; tapping the top metrics opens advanced options and long-press opens logs.
- U=36dp; top bar 1U; side volume keys each 1U x 1.5U, power 1U x 3U; bottom 11U total with 1U start/immersive circles and three 3U mode keys. Missing root image displays a full-screen #99000000 overlay and opens ACTION_OPEN_DOCUMENT on tap.
- Display geometry is based on the full host screen: 1:1 is 1125x2436 and only enabled when host width/height exceed those dimensions; shrink uses 80% of host width; fullscreen uses aspect-preserving min(width/1125,height/2436). Mode and immersive controls remain usable while the VM runs. Immersive hides all controls and tapping the screen restores them.
- Host status strip samples whole-device CPU from `/proc/stat` deltas and RAM from `/proc/meminfo` MemAvailable once per second. Timer starts on VM launch and returns to zero when stopped. Start is disabled during VM execution; stop and snapshot remain on the existing Back-key confirmation path. Side volume/power keys provide pressed-state feedback only and have no hardware action.
- CI: run `43086133597` succeeded on APK commit `698ed076a96299944fa4b317655dc738c4c89a41`; artifact `vmios-debug-apk-with-engine` ID `13886706807`, 56,802,009 bytes. APK SHA-256 `f6b32d2b84165144c31d55ee0925e56b2a3639b77ccde1c5cb3674995e51479f`; embedded Host Raster payload SHA-256 again verified as `7204f83ba7a8370f1134915fc2f47516e66366e038c2d6f62bb06d5b584c4e8d`. Guest Local is not included.
- Intermediate CI failures were caught and corrected before final success: duplicate `placeholderControl`, Android Activity method collision for `setImmersive`, and follow-up listener/geometry behavior. Earlier successful builds on intermediate commits are superseded by the final artifact above.
- No device screenshot or runtime interaction test has been performed. The exact pixel layout, touch-to-restore behavior, CPU/RAM readings, and first-launch extraction still need on-device confirmation. No changes to `main`, `payload`, or `xun`; no XNU build.


## 2026-11-12 display/offload corrective patch
- Adaptive button geometry: `U=min(dp(36), round((screenWidthPx-dp(8))/9.5))`; middle mode key width `Mid=2.5U`; bottom bar width `2U+3Mid=9.5U`. Side controls use the same U. Bottom button font scales to `0.34U` px.
- Immersive visibility now explicitly preserves `displayArea`; a framebuffer touch listener restores controls on ACTION_UP while consuming that tap only during immersive mode, so normal guest touch is unchanged outside immersive mode.
- Child ordering corrected: displayArea is added before the top metrics strip so the top strip remains visible above the framebuffer.
- Native framebuffer offload default changed to false. A one-time SharedPreferences migration also disables the old implicit-true setting on existing installs, then respects the user's later saved choice.
- Per user instruction, no startup progress overlay was added.
- Source commits: MainActivity layout `84e5d778`, BootConfig safe default `b699c38c` plus one-time migration `23f7b1ad`, immersive framebuffer touch `b5a049fa`.
- Final CI: run `43145023104` succeeded on source commit `b5a049faa745aecd4104995571481ebf177282c5`; artifact `vmios-debug-apk-with-engine` ID `13911560772`, 56,802,721 bytes. APK SHA-256 `dcfa6b58999441c75bfa92db2fee83ffa9796be73a1eb37cc8d80ff341a95d93`; bundled Host Raster SHA-256 verified as `7204f83ba7a8370f1134915fc2f47516e66366e038c2d6f62bb06d5b584c4e8d`. Guest Local excluded.
