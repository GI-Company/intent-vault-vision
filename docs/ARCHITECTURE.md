# INTENT Vault Vision architecture

## What the app does
- CameraX live preview and high-resolution ImageCapture.
- Bundled ML Kit face detection estimates smiles for each visible detected face; never assumes no faces means all smiling. User must enable Auto Smile; requires 3 consecutive sampled detections above 0.75 for a stable group count and a cooldown.
- EmbeddingGemma 2 full 740M generates local image and text embeddings, enabling semantic scene matching and private note search.
- On-device notes and pictures use app-private storage. Embeddings are semantic similarity scores, **not smile probabilities**.
- An assistant command router can save notes, switch modes and trigger a user-initiated web search. **This is NOT a generative LLM.** Full local free-form responses require shipping another quantized instruct model and its inference runtime.
- Web tab uses an embedded WebView, deliberately connecting to an external search engine. Camera frames and notes are not uploaded by this tab.

## Build-time model distribution
Weights are NOT in git history. GitHub Actions downloads the full 740M compiled Apache-2.0 LiteRT artifact at *build time*, checks that a real model is present, embeds it in Android assets, and uploads the APK. Installed APKs never fetch weights. A source build without running the model-preparation script compiles but cannot embed scenes.

For reproducible releases pin EMBEDDINGGEMMA_REVISION to a reviewed commit, verify SHA-256 of the model and retain third-party notices. The install is large (~485 MB weights before APK overhead). Android Play delivery limits may require install-time Play Asset Delivery for future multimodel variants; install-time packs don't cause a second in-app download.

## Safety and limitations
Smile probability is fallible. Faces missed by the detector cannot be evaluated; verify group visibility before trusting a capture. No face identification, enrollment or face recognition. Enable only for consenting subjects. Avoid use for safety-critical decisions.

No API keys are shipped, and the search function does not silently leak private notes to the internet. A second local generative model is necessary for conversational writing, tool planning and document question-answering.
