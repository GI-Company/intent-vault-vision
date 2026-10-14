# INTENT Vault Vision

**Native, private-first Android AI camera + assistant toolkit** using **EmbeddingGemma 2 740M (full text, image, audio model)**, a bundled ML Kit smile detector, CameraX, and Jetpack Compose.

Source: [GI-Company/intent-vault-vision](https://github.com/GI-Company/intent-vault-vision)

## Implemented in source
- Live CameraX preview with front/back switching and full-resolution photo capture.
- Opt-in **Auto Smile**: detect all visible faces with bundled ML Kit and trigger the real shutter only after 3 consecutive samples where all detected faces have smiling probability >= 0.75. 8-second cooldown. No faces = no capture.
- EmbeddingGemma 2 **740M** image-to-text similarities sampled from the camera (~0.9s); all inference local after model is packaged.
- Personal Vault: save notes in private app storage, use local semantic note matching through EmbeddingGemma.
- Assistant command panel for camera control, notes, semantic recall, and an explicit external web search.
- Embedded web search view; network access only on the Web surface when the user chooses to search.
- Material 3 light UI and native Android code.

## Essential model distinction
**EmbeddingGemma 2 is an embedding model, not a generative conversational model.** The existing Assistant screen is a capable **local command router**, but free-form question-answering and synthesized web summaries require an additional bundled generative LLM and its inference integration. We do not mislabel retrieval or commands as LLM-generated answers.

## Install without a second model download
The release workflow downloads `embeddinggemma-2-740m.litertlm` **during the GitHub Actions build** and embeds it into the APK as a compressed-resource-exempt Android asset. The installed app neither fetches model weights nor needs to sign into Hugging Face. The **source repository intentionally excludes large weights**.

1. Open **Actions → Android debug (model included) → Run workflow** (or push to main).
2. Download the `INTENT-Vault-Vision-debug-with-model` artifact if the workflow finishes successfully.
3. Install the APK on a compatible Android device. You will be asked for camera permission; enable Auto Smile explicitly if desired.

The full 740M LiteRT bundle is ~485 MB, so the output APK is **large**, and some devices may lack the RAM/accelerators required for acceptable throughput. It is not a Play Store release yet. For Google Play, use install-time Play Asset Delivery where app bundle download limits require it.

For local builds, run `python -m pip install huggingface_hub`, `python scripts/prepare_model.py`, then `gradle :app:assembleDebug` with Gradle 8.10.2 and Android SDK 35. A plain source checkout without this build-time step will not contain weights.

## Validation status
This is a development implementation. Android compilation, app signing, device profiling, and live face-detection accuracy still require CI/device testing. Do not infer success from a committed file.

## References
- [EmbeddingGemma 2 full 740M model (Apache 2.0)](https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm)
- [MediaPipe Universal Embedder Android guide](https://developers.google.com/edge/mediapipe/solutions/retrieval/universal_embedder/android)
- [ML Kit bundled face detector](https://developers.google.com/ml-kit/vision/face-detection/android)
- [Google Play download size limits](https://support.google.com/googleplay/android-developer/answer/9859372)

See [architecture](docs/ARCHITECTURE.md) and [AI Studio import](AI_STUDIO_IMPORT.md).
