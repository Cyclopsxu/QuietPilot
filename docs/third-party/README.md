# Binary distribution notices

QuietPilot retains ShadowAuto attribution and Apache-2.0. Binary attachments also include third-party runtime components; the project license does not replace their applicable notices.

| Component | Source / notice |
| --- | --- |
| AndroidX ViewPager2 and transitive AndroidX components | AndroidX, Apache-2.0; project Apache text included |
| Gson 2.11.0 | Google Gson, Apache-2.0 |
| OkHttp 4.12.0 / Okio and Kotlin runtime dependencies | Square / JetBrains, Apache-2.0 |
| Paddle Lite v2.10 | `https://github.com/PaddlePaddle/Paddle-Lite/blob/v2.10/LICENSE`; original text in Paddle-Lite-LICENSE.txt |
| PP-OCRv2 assets / PaddleOCR source | `https://github.com/PaddlePaddle/PaddleOCR/blob/release/2.4/LICENSE`; original text in PaddleOCR-LICENSE.txt |
| OpenCV Android SDK 4.2.0 | License copied from the downloaded OpenCV distribution during packaging |
| libc++ / OpenMP / Android NDK runtime | Notices copied from NDK 23.1.7779620 during packaging |
| HiAI runtime files supplied in the official Paddle Lite archive | Retained as supplied with the upstream OCR runtime archive; vendor components retain their applicable rights, not relicensed by QuietPilot |

Paddle runtime download: `https://paddleocr.bj.bcebos.com/libs/paddle_lite_libs_v2_10.tar.gz`.
Models: `https://paddleocr.bj.bcebos.com/PP-OCRv2/lite/ch_PP-OCRv2.tar.gz`.
Dictionary: `https://paddleocr.bj.bcebos.com/dygraph_v2.0/lite/ch_dict.tar.gz`.
OpenCV archive: `https://paddlelite-demo.bj.bcebos.com/libs/android/opencv-4.2.0-android-sdk.tar.gz`.

The release ZIP includes original project LICENSE/NOTICE and a `licenses/` directory. The same notices are added under `assets/quietpilot-licenses/` in both APKs before signing. No signing key or API configuration is distributed.
