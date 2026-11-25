# Aegis Browser

A minimal, dark-first, zero-telemetry Android browser built with Kotlin, Jetpack Compose, real client-side End-to-End Encryption (E2EE), and request-level ad/tracker interception.

## Architecture & Security

- **Ad & Tracker Interception (`AdBlockerEngine` + `SafeWebViewClient`)**:
  - Intercepts requests in `shouldInterceptRequest` and `shouldOverrideUrlLoading` before network dispatch.
  - Blocks known ad networks, analytics scripts, tracking pixels, fingerprinting endpoints, popup windows (`setSupportMultipleWindows(false)`), and strips tracking query parameters (`utm_*`, `fbclid`, `gclid`, etc.).
  - Supports global shield toggle, per-site allowlisting, and custom domain blocklists.
- **URL & WebView Hardening (`UrlValidator`)**:
  - Rejects `javascript:`, `file:`, `content:`, `data:`, `intent:`, and `vbscript:` navigation schemes.
  - Disables third-party cookies, mixed HTTP/HTTPS content, file access, and geolocation by default.
- **End-to-End Encryption (`CryptoEngine` + `KeyVault` + `EncryptedStore`)**:
  - **Key Derivation**: `PBKDF2WithHmacSHA256` (310,000 iterations, random 128-bit salt).
  - **Asymmetric Key Exchange**: Ephemeral `X25519` ECDH combined with the passphrase key via `HKDF-SHA256`.
  - **Authenticated Encryption**: `AES-256-GCM` with 96-bit random IVs and AAD version binding (`AEGIS-E2EE-V1`).
  - **Key Storage**: Device X25519 private key is wrapped at rest using an `AndroidKeyStore` AES-256-GCM key.
  - **Zero-Knowledge Sync**: Export/import emits only `EncryptedEnvelope` JSON ciphertext; plaintext and private keys never leave the device.

## Limitations of E2EE Scope

- E2EE protects stored browser state (bookmarks, history, open tabs, encrypted notes, and custom filter rules) at rest and during sync export/import.
- Live web traffic relies on standard HTTPS/TLS between the Android `WebView` and destination websites; destinations still see your IP address unless routed through an external VPN or Tor proxy.
- If you forget your vault passphrase, encrypted data cannot be recovered (there is no escrow or backdoor).

## Local Build & GitHub Actions APK Workflow

1. **Run unit tests**:
   ```bash
   gradle testDebugUnitTest
