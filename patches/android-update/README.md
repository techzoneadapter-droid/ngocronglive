# Android updater repair (not released)

Public checks on 18 November 2026: manifest advertises 1.0.39/code40,
388503606 bytes, SHA256 3d9a1ad7484177f813f9d8555bf17ae9daaaf212477696498ed5aa102efd93d4.
APK GET/HEAD return 200. GET is chunked, has no Content-Length, and ignores Range.
This explains unreliable Unity downloadProgress, but does not establish why the
reported phone's download request fails. No device log has yet been captured.

NeroAppUpdate.cs computes progress from the partial file against manifest bytes,
allows 15 minutes for a 388 MB download, reports HTTP status and logs request error,
and handles initial storage errors. Existing size/SHA256/package/origin checks stay.
Compiled against the shipped Unity managed assemblies with full Unity reference
assemblies. Copied into the mobile Unity project. APK has NOT been rebuilt,
installed on Android, or published. Existing installed clients still need the old
updater or the website download to install a new APK.

Capsule report: user identifies Bulma future. Local catalog sells detector379 for
100,000,000 gold (no bundle options), not capsule380. No capsule purchase fix is
claimed; actual failing request and live catalog still need verification.
