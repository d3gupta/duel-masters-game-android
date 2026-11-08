# Android release build and signing

## Build outputs

The Android app uses application ID com.duelmasters.game, minimum Android API 26,
target Android API 36, Java 17 and version 1.0.0. The Android module declares no internet permission and has Android backup disabled.

CI runs:

1. :app:assembleDebug
2. :app:assembleRelease (R8 minification and resource shrinking enabled)
3. :app:testDebugUnitTest
4. :app:connectedDebugAndroidTest on an API 36 emulator, including a forced offline
   import of the bundled SQLite catalogue into Room.

Branch pushes produce a **validation build artifact**, not a production release. When no signing secrets are configured, it contains `app-release-unsigned.apk` and must not be distributed. The artifact is retained for 30 days.

## Signing

Never commit a keystore or signing password to this repository.

For a local signed release build, supply these environment variables before running
gradle :app:assembleRelease:

- DM_RELEASE_STORE_FILE: path to your release keystore.
- DM_RELEASE_STORE_PASSWORD
- DM_RELEASE_KEY_ALIAS
- DM_RELEASE_KEY_PASSWORD

For GitHub Actions, add repository Actions secrets with these exact names:

- DM_RELEASE_KEYSTORE_BASE64: base64-encoded keystore file.
- DM_RELEASE_STORE_PASSWORD
- DM_RELEASE_KEY_ALIAS
- DM_RELEASE_KEY_PASSWORD

When all four signing settings are present, CI restores the keystore into the runner's
temporary directory and signs the release APK. When none are present, a branch validation
build is unsigned. A `v*` release tag is rejected unless all four secrets exist, and the
workflow verifies the resulting APK signature with `apksigner` before uploading the
release artifact. The pipeline never uses the debug signing key for a production release.

## Release checklist

Use the maintained [QA and release sign-off checklist](qa-release-checklist.md) for the complete automated, gameplay, device and distribution gate. The emulator CI run also publishes a versioned catalogue-only card inventory for rules review.


- [ ] Configure the production keystore in local environment or GitHub Actions secrets.
- [ ] Confirm the CI run passed debug build, optimized release build, JVM tests and
      emulator catalogue-import test.
- [ ] Install the debug QA APK on physical Android 8.0+ devices and verify first-launch
      import, catalogue search, deck save/edit/delete, AI match, local pass-and-play,
      blocker decisions and Shield Trigger decisions.
- [ ] Test airplane-mode operation after a clean install.
- [ ] Treat the debug QA APK as test-only; use a production-signed release APK for distribution.
- [ ] Verify the card-source permissions, card artwork permissions (if added later),
      app icon, store listing and Duel Masters trademark use before distribution.
- [ ] Confirm the cards shown as playable match the intended rules/format. Unsupported
      cards are intentionally catalogue-only until their effects are implemented.

## Scope statement

The release build is an offline Duel Masters rules slice with a searchable English
catalogue. It does not claim complete effect coverage for the entire catalogue.
Cards are deck-eligible only when the local importer classifies their known rules text
as supported. The bundled catalogue does not include official card artwork.
