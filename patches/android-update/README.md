# Android updater repair (not released)

Public checks on 18 November 2026: manifest advertises 1.0.39/code40,
388503606 bytes, SHA256 3d9a1ad7484177f813f9d8555bf17ae9daaaf212477696498ed5aa102efd93d4.
APK GET/HEAD return 200. GET is chunked, has no Content-Length, and ignores Range.
This explains unreliable Unity downloadProgress, but does not establish why the
reported phone's download request fails. No device log has yet been captured.

NeroAppUpdate.cs computes progress from the partial file against manifest bytes,
allows 15 minutes for a 388 MB download, reports HTTP status and logs request error,
and handles initial storage errors. Existing size/SHA256/package/origin checks stay.
Unity editor ran the actual DownloadRelease coroutine against the public APK:
388503606 bytes and SHA256 verified, progress reached1. HTTP404, foreign origin,
truncated file and SHA256 mismatch were rejected. Android1.0.40/code41 built
successfully after retrying an ARMv7 clang crash. apksigner verifies the same
certificate as1.0.39 (SHA256 1a9a92c747a005d850b7b1c3ca2afa58858f23e3e3929172c63ee5a58c9468f3).
APK SHA256 c9c287da2e008b55aadb6cde7c52ed391c08e30093168aeef6161fb0db1a01ac,
388509672 bytes. Artifact: outputs/NERO-ANDROID-UPDATE-REPAIR/Ngoc-Rong-NERO-Android-1.0.40.apk.
No Android device/emulator was connected; installation/on-device download is NOT
verified. APK is not published. Existing installed clients still need the old
updater or website download to receive the repair.

Capsule report: VPS diagnostic confirms Bulma future sells detector379 for
100,000,000 gold (no bundle options), not capsule380. Native local buyItem(379)
receives1 and debits100M. The old multi-buy hook rejected non-Santa shops;
NpcBundlePurchase now allows ordinary gold/gem stock at every NPC. Native local
packet with quantity3 at Bulma receives3 and debits300M. Specialized exchanges,
capacity/limit/quest services retain their native one-at-a-time flow pending
dedicated atomic batch adapters; do not claim every special service supports bulk.
