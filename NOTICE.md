# Credits and provenance

This repository packages original local helper tools and a documented investigation on the owner's TrueID Skyworth HPH07. It is independent of True, Skyworth, Google, Warner Bros. Discovery and the projects below.

- **cleanyourtv**, by Gathin and contributors: the launcher, configuration schema, TV cleanup workflow and base64-transfer design. Tested revision: `30626a8e4714f86bf837384b109b5c73ee736917`. Upstream: https://github.com/Gathin23/cleanyourtv. Its MIT notice is retained in `licenses/cleanyourtv-MIT.txt`. `launcher/hph07-config.xml` is an adapted upstream file, and `tools/tv.py` adapts its chunked transfer algorithm. The launcher itself is fetched at the pinned commit, not claimed as our invention or copied into this repository. Its Outfit font retains the upstream SIL OFL license.
- **REA**, by morluto and contributors: a reference for APK reverse-engineering workflows, not a remote unlock tool. Consulted revision: `8b478d0f15bf585d0570ebdcadb4d5857cc86cb4`. Upstream: https://github.com/morluto/rea. No REA source is redistributed here.
- **JADX**: APK investigation used the CLI entry point `jadx.cli.JadxCLI`. No decompiler binaries or decompiled vendor sources are redistributed. https://github.com/skylot/jadx
- **adb-shell** and **androidtvremote2** supply the Python ADB and authenticated remote transports. They are installed as dependencies and retain their own licenses.

No vendor APK, proprietary firmware, DRM key, account, signing key, ADB private key, raw device backup or captured login screen is included. The public source includes portability changes described in `docs/validation.md`; the exact private working session remains outside this Git repository.
