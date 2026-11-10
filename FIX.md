# FIX — vmios change/build ledger

> Hard limit: 900 KB. Keep entries concise. Before every repository operation, read this file first.
> Branch names are user-owned: never rename or delete branches without explicit instruction.

## Standing instructions
- Only make requested changes; do not silently add/remove features.
- Keep `apk`, `payload`, and `xun` branch names unchanged; do not delete branches.
- Never treat CI success as proof that the Android device problem is fixed.
- 550/551: evidence probe only. Do NOT change their decode mapping or assume BGR10A2Unorm.
- 554/555: leave unchanged.
- Record every code change, cancellation/reversal, build workflow/run, and GitHub operation requested by the user.
- Keep this file below 900 KB; compact/archive old entries before approaching the limit.

## Current baseline
- Working branch: `fix/mobilegestalt-boot-and-side-controls`
- Intended base from prior session: `1cea8fe7c89c1d3d9dadb994066091197e13c439`
- Existing plugin source already has an IOSurface-first offscreen allocation attempt (`sweet_render_surface_create`, FourCC `0x42475241`) with heap fallback, and already checks `r.tex_mapped` before the 1 MiB upload path. Do not claim these were newly added in this operation.
- Existing XNU patch: `xnu/patches/0097-sweet-gpu-ca-draw.patch`; it contains SweetSurface memory allocation and selector 0x104 error propagation hunks. Device logs previously lacked the expected allocation marker, so actual patch application/kernel artifact provenance still needs verification.

## 2026-11-11 operation log
### User requirements
- Create this root-level `FIX.md`; read it before each future operation.
- Record modifications, cancellations, build start location/workflow, and GitHub operations/instructions.
- Keep file under 900 KB.
- Add the requested fmt550 probe so runtime data can diagnose the texture; do not alter decoding.
- Fix offscreen IOSurface/host-mapped rendering and start the relevant build.

### Findings before edits
- `SweetMetal.m` already contained a fmt550 dump probe, but it read 4096 words as tightly packed data without validating mapping extent or row stride. This is unsafe and does not reliably count a strided IOSurface.
- `SweetTexture.ensureRenderPixels()` already tries IOSurfaceCreate with BGRA FourCC, then falls back to calloc.
- Render submission already checks `r.tex_mapped` before the <=1 MiB heap upload branch.
- `0097-sweet-gpu-ca-draw.patch` already contains the kernel allocation hunk and selector 0x104 returns the real map result. Need ensure XNU build actually applies this patch and that payload consumes the matching XNU artifact; do not confuse plugin-only build with kernel fix.

### Planned edits in this operation
- Make fmt550 probe one-shot and read only when the source is a mapped texture with valid 64x64 dimensions, sufficient row stride, and readable mapped extent. Print first 48 raw words; count top two bits over all 64x64 pixels using actual stride. No decoding changes.
- No change to 550/551 decoding, 554/555 decoding, tfn, or unrelated rendering behavior.
- Do not claim IOSurface creation/mapped-first logic is new; it is already present in baseline. Verify kernel patch application/artifact chain separately.

### Build tracking
- Payload workflow: `.github/workflows/build-vmios-full-gpu-payload.yml`; push to this branch touching `vm/metal/SweetMetal.m` triggers it.
- XNU workflow: `.github/workflows/xnu.yml`; push to this branch touching `xnu/patches/**` triggers it.
- Payload workflow on a plain push currently falls back to the hard-coded XNU run ID `42853907291`; only a successful XNU `workflow_run` event automatically passes its exact run ID. Do not accept a payload built from a stale kernel as validation of the kernel fix.
- Build runs started this operation: none at file creation time. Add actual run IDs/URLs and artifact names only after GitHub reports them.

### Cancellations / reversals
- Explicitly cancelled: changing 550/551 to BGR10A2Unorm. Do not reintroduce.
- Leave 554/555 unchanged.
