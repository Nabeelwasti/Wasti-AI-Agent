# Model archive policy

The registry currently lists one candidate checkpoint repository for each requested family. These are candidates, not approved downloads.

Before changing a family to `license_review: approved`:

1. Open the exact upstream model card and license file at the selected immutable commit.
2. Record the immutable commit hash in `revision`.
3. Record the exact license identifier and a copy/link to the license text.
4. Review commercial use, attribution, acceptable-use, redistribution and derivative-work conditions.
5. Check whether gated access or a click-through agreement applies.
6. Decide whether the files may be kept privately, backed up, redistributed or served to customers. These are separate permissions.
7. Confirm the selected format is compatible with the chosen runtime and the target device.

Only after review should the guarded downloader be run:

```bash
cd models
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python download_checkpoint.py --family qwen
```

The tool downloads a full snapshot at the pinned revision and creates SHA-256 checksums. Verify the manifest after download and store a second encrypted backup outside the phone.

Do not place weights in Git commits. Keep weights in a private archive on a PC/external drive or object storage that you control. Phone storage should contain only compatible models selected for that device.
