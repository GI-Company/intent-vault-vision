#!/usr/bin/env python3
"""Download one audited LiteRT bundle at BUILD time; never at app runtime."""
import hashlib
import os
from pathlib import Path
import shutil
from huggingface_hub import hf_hub_download

repo = "litert-community/embeddinggemma-2-740m-litert-lm"
filename = "embeddinggemma-2-740m.litertlm"
revision = os.environ.get("EMBEDDINGGEMMA_REVISION", "main")
dest = Path("app/src/main/assets/models/embeddinggemma-2-740m.litertlm")
dest.parent.mkdir(parents=True, exist_ok=True)
src = hf_hub_download(repo_id=repo, filename=filename, revision=revision,
                      token=os.environ.get("HF_TOKEN") or None)
shutil.copyfile(src, dest)
if dest.stat().st_size < 400_000_000:
    raise SystemExit("Invalid or incomplete 740M model; aborting APK packaging.")
digest = hashlib.sha256()
with dest.open("rb") as handle:
    for part in iter(lambda: handle.read(8 * 1024 * 1024), b""):
        digest.update(part)
actual = digest.hexdigest()
required = os.environ.get("EMBEDDINGGEMMA_SHA256")
if required and required.lower() != actual:
    raise SystemExit("Model SHA-256 mismatch; refusing to build.")
print(f"Bundled {dest}: {dest.stat().st_size:,} bytes; SHA256={actual}")
