# Refract
[![Download Nightly](https://img.shields.io/badge/Download-Nightly-blue.svg)](https://nightly.link/keegannhat/Refract/workflows/ci/main/Refract-Debug.zip)

Android decoder for Dolby Atmos files, currently supporting **DD+JOC** (both streaming and Blu-Ray) and **Dolby AC-4** IMS (binaural). Works offline and does not require any external apps.

The app requires and utilizes your phone's licensed OEM decoders (which is used by streaming platforms to playback Atmos) via MediaCodec().

# Supported formats
* **E-AC3-JOC (Dolby Digital Plus Atmos)**: Supports both the standard Online Media profile (.ec3, found on streaming platforms) and the Blu-Ray profile (.eb3). Max decode layout is 7.1.4 (12 channels), due to Android limitations.

* **Dolby AC-4 IMS (Immersive Stereo):** Can be decoded to 2.0 (stereo) max.

Dolby AC-4 IMS decoding may only works on Samsung devices running Android 11 or higher. Google Pixels and few Chinese phone brands can't function yet due to lack of Dolby licenses.

# Known issues
Decoding a DD+JOC file to 9.1.6 with "Multichannel WAV" export format will output a 16-channel WAV file with 4 empty channels.
