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
- Payload: dispatch-only workflow; removed push/workflow_run triggers because they cannot safely supply a matching XNU run ID. Manual dispatch requires explicit `xnu_run_id`; removed stale default `42853907291`. Workflow commits `4044d46a89aeb01c102b933a1aeaaa5c4c74f305` (initial branch/input correction) and `8d241a27785c7ae50a909e66c83e42360eb7a8b9` (dispatch-only safety correction). No payload build dispatched yet.


## 2026-11-11 A1 offscreen IOSurface routing
- Review result: A1's core design is valid, but the repository already had IOSurface allocation/lifetime support on `apk`; the missing path was host routing of an owned internal render-target IOSurface. Do not duplicate the allocator.
- APK source fix: `vm/metal/SweetMetal.m` commit `4d3406fef4fa7a6c6c8071c3e400a4af858424d6`. Host routing now treats either a cross-process mapped IOSurface or an owned internal render-target IOSurface (`ownsRenderSurface`) as directly mappable and sends its surface ID to `sweet_ca_map`. The 1 MiB ceiling remains only for heap-backed fallback uploads; it no longer blocks IOSurface-backed textures. Actual IOSurface bytesPerRow is preserved.
- Payload review: `android/app/src/main/java/dev/vmios/sweetvm/CaCompositor.java` already avoids reusing decoded pixels for `F_INTERNAL` surfaces and reuses the output array, so no Java change was needed on `payload`.
- Wired budget: IOSurface lifetime is released by `SweetTexture::releaseRenderSurface`/dealloc. No LRU eviction was added: evicting a live CA-owned render target would invalidate an active texture. The 1536 MiB cap and allocation-failure heap/local-raster fallback remain. The repository carries only a patch for `SweetSurface.cpp`, not the full `SweetSurface::free` implementation, so the kernel-side wired-counter decrement and the `allocSize > 0 => memory != NULL` invariant cannot be independently certified from this checkout; they must be verified against the XNU build source before claiming the budget prerequisite complete.
- Scope correction: exploratory Metal/Java edits made on `xun` during this pass were reverted because those files belong on their APK/Payload build branches. Reverts: `b3c3d7bfdf48ceb55a0b213d3c261a0dcf2aa7c9` and `a2a9e8cc40a65ea9080a334caed29c192ec2ac98`. The XNU patch was not changed in this A1 pass.
- Build/device validation: not yet verified. The APK source commit is committed; do not treat it as a successful APK build or device test. No new XNU/Payload build is claimed.


## 2026-11-11 Payload rebuilt against XNU #55
- Verified XNU run `42952041760` on branch `xun`, commit `286b7cfbe2eea6893f5e20790945b084f5aac81b`, conclusion `success`; confirmed non-expired `xnu-kernel` artifact (artifact ID `13825901503`, 60,814,408 bytes).
- Payload workflow still had a push trigger and stale fallback `42945105533`, contradicting the dispatch-only safety note above. For this one build, changed the fallback to the exact successful XNU run `42952041760`; commit `75279aef150cf5676ce66c648c42cf1ed80467e3` triggered Payload run `42953876179`.
- Immediately restored safe workflow behavior in commit `0523009176fa8f82cbf9fbc6c341099958878e77`: removed push trigger and removed all fallback XNU IDs; future Payload builds require explicit `workflow_dispatch.inputs.xnu_run_id`. This workflow-only follow-up should not trigger another Payload build.
- Payload run `42953876179` was observed `in_progress` at 2026-11-11 03:36 UTC; final status and artifacts must be checked before reporting completion. It was triggered from commit `75279aef150cf5676ce66c648c42cf1ed80467e3`, whose workflow explicitly selects XNU run `42952041760`.


## 2026-11-11 internal render-target decode cache hotfix
- Verified current `CaCompositor.java` had `if (!uploaded && !internal)`, forcing every F_INTERNAL sample draw to decode the entire surface again. Changed cache hit to reuse any non-upload decoded surface during the current frame; added `Decoded.internal` and explicit internal-entry eviction in `onFlush()`. Existing non-upload eviction remains, so ordinary IOSurface surfaces also retain their prior frame-boundary invalidation behavior.
- This is the frame-boundary hotfix only. It does not solve multiple guest rewrites of the same surface within one frame; a guest texture-generation protocol would be the more complete follow-up, but is intentionally out of scope here.
- Payload workflow is dispatch-only and requires an explicit successful XNU run ID. Source commit does not trigger a Payload build; do not add a push trigger or stale fallback. Build/device validation remains pending.


## 2026-11-11 payload build-source correction
- Confirmed `.github/workflows/build-vmios-full-gpu-payload.yml` checks out the `payload` branch and runs `tools/build-sweetmetal.sh`; the APK workflow does not reference/build `vm/metal/SweetMetal.m`. Therefore the effective IOSurface threshold/log-throttle change is also committed to `payload`, alongside the `CaCompositor.java` cache hotfix. The equivalent change on `apk` is only a source mirror and is not evidence that an APK contains the Metal plugin change.
- Payload source commit: 2481d12256fd61c2c078106901b9f0f4708a93d3. Payload FIX update follows. The payload workflow remains dispatch-only; no build was started by these source pushes.


## 2026-11-11 Payload provenance hardening and automatic XNU handoff
- Payload workflow validates that the supplied XNU run is completed/successful, originates from branch xun, and uses .github/workflows/xnu.yml before downloading xnu-kernel.
- Each Payload artifact includes XNU-PROVENANCE.txt with XNU run ID, source commit, and kernel SHA-256; SHA256SUMS contains the kernel hash and the Actions job summary reports the same provenance.
- Normal builds are dispatched automatically by the XNU workflow after the verified kernel artifact is uploaded. Manual dispatch remains a recovery path and still requires an explicit run ID.
- The malformed 0097 patch and XNU source-tree verification gate are recorded on xun/FIX.md; this branch change only hardens provenance and packaging.


## 2026-11-11 ring10 IOSurfaceRelease symbol fix
- User supplied ring10 evidence: `sweet_render_surface_create` failed its required-symbol gate because `dlsym(ios, "IOSurfaceRelease")` returned NULL; IOSurface objects are CF objects and must be released with CoreFoundation `CFRelease` when the optional IOSurfaceRelease export is absent.
- Applied to the actual Payload source branch (not `xun`, whose SweetMetal.m does not contain `sweet_render_surface_create`): commit `1e45716ec6b0faeeafcfd5f957555d9f7df916da`, file `vm/metal/SweetMetal.m`.
- Removed `release_surface == 0` from the required-symbol condition. Both failure cleanup paths now use `IOSurfaceRelease` when exported, otherwise `cf_release`. No changes to allocation dimensions, render routing, or heap fallback.
- Source-level verification: required-symbol gate no longer includes `release_surface`; both cleanup paths contain the CFRelease fallback. Build/device validation is pending; do not claim IOSurface allocation succeeds until a fresh Payload artifact reports successful render-target creation and host routing.
- Compute blur is NOT implemented by this commit. The current Payload source still returns `SweetOpaque` for compute PSOs and an empty `SweetCommandEncoder` for compute encoding. The function names identify candidate kernels but do not establish texture indices, input/output aliasing, pixel formats, or dispatch dimensions. Implementing a guessed CPU convolution now risks corrupting CA render targets. Next safe step is to instrument/store the compute PSO name, texture/buffer bindings, and dispatch dimensions, then implement the observed contract behind an opt-in switch (default off). No claim of blur support.
