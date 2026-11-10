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
- Payload workflow must listen to `payload`; manual dispatch must require an explicit XNU run ID to prevent stale kernel.
- XNU workflow must listen to `xun`.
No source code has yet been ported in this branch. Record actual commits and runs below.


## Workflow correction commits
- XNU: push trigger now listens to `xun` instead of `main`; commit `be6516ea6a4bffe36076eca956c5e55b7965fbd0`. The push is expected to start the XNU build; verify its run and artifacts before using it.

- Branch build triggered by XNU workflow correction: run `42900388186`, head `be6516ea6a4bffe36076eca956c5e55b7965fbd0`, in progress at verification. URL: https://github.com/wangyitao978-blip/vms/actions/runs/42900388186. This is a workflow correction build, not proof that the kernel patch is included or the device issue is fixed.
