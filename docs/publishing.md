# What belongs in the public repository

Use explicit source/doc paths for staging and inspect the staged diff. Keep `.state/`, `.deps/`, build outputs, APK extracts, firmware, backups, screenshots showing accounts/networks, signing keys, remote certificates and Bridge tokens private. The ignore file helps with accidents; it does not sanitize a file copied into a new name.

The initial public export was assembled from a source allowlist in a new folder, not by making the original working session public. Source points to upstream projects at pinned revisions and retains their license notices. Read the checked-in validation results to distinguish tested real-device behavior from packaging adaptations.

If publishing an example screenshot, inspect the actual image for account/profile names, serials and Wi-Fi names. The original login/profile screenshots are deliberately absent. Blog drafts should not quote raw account/device dumps. Describe Binder behavior from the investigation without redistributing proprietary decompiled APK sources or configuration/serial lists.

The GitHub repo is the home for source and the Markdown article. Publishing the article to a separate blogging platform is a separate action requiring the author's chosen destination and account; a public Markdown article in this repo already supplies a shareable text version.
