# Google AI Studio import

Import https://github.com/GI-Company/intent-vault-vision using Build > Add files > Import from GitHub. Choose Android / native Kotlin Compose if offered.

The source includes Android/CameraX and MediaPipe APIs that require a real Android build. Google AI Studio previews do not establish camera, ML Kit or LiteRT device compatibility.

**Build prompt:** Preserve native Jetpack Compose, CameraX, bundled ML Kit smile detection and app-private storage. Keep image/text EmbeddingGemma 2 740M inference on device. Never download models after installation. Improve UI/accessibility without replacing native Android with a mock web camera. The Web tab may access external sites only after explicit user action. Never claim embeddings generate chat text.
