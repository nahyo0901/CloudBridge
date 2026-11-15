# Publishing CloudBridge on GitHub, Play and Obtainium

CloudBridge uses the `oss` release flavor and application ID
`io.github.howielyn.cloudbridge`.

## Signing identity

The existing release APKs use this public certificate SHA-256:

```text
a352eceb86e3d2fdd6a68ff68ab0637e978b843c8d885fa79e87e16b0cd06993
```

In **Play Console → CloudBridge → Play app signing**, compare the **app signing
key certificate** SHA-256 with that fingerprint. The app signing certificate
is the identity users install; the **upload key** only authenticates AAB
uploads. Keep the release keystore and passwords private and backed up.

## GitHub build artifacts

The GitHub workflows are manually triggered and do not publish releases:

- **Android CI - Debug APK** builds installable debug APKs.
- **Android CI - Unsigned Release APK** builds unsigned release APKs for
  validation. These are not signed distribution artifacts and should not be
  used to update a signed installation.
- **Clean Build (No Cache)** verifies a clean debug build and does not upload
  an APK.

For distribution, configure the existing release keystore through
`keystore.properties` or the `CB_*` environment variables, then build signed
artifacts locally:

```sh
./gradlew assembleOssRelease
./gradlew bundleOssRelease -PnoAbiSplits
```

Do not pass `-PunsignedRelease` for distributable artifacts. Update
`versionName` and `versionCode` together for a release. ABI APKs add 6–9 to the
base version code; universal APKs and the Play AAB use the base code.

## Google Play

1. Build the signed OSS AAB using the release key that matches the Play app
   signing certificate.
2. In Play Console, upload it to an internal testing track and add release
   notes. Each upload needs a new, increasing version code.
3. Complete the app listing, screenshots, contact details, privacy policy,
   content rating, target audience, ads and Data safety sections based on the
   app's actual behavior.
4. Complete the all-files-access declaration for `MANAGE_EXTERNAL_STORAGE`,
   explaining the file-manager and sync features.
5. Test provider login and sync through the internal testing track, then
   promote the release through the required testing tracks and production.

Internal app sharing uses a different signing identity and is not a
certificate-compatibility test.

## Obtainium

The [Obtainium configuration](obtainium.json) tracks signed stable universal
APKs from this repository's GitHub releases. Development prereleases, AABs,
architecture-specific APKs and checksums are excluded. Review and confirm the
configuration before installing or updating.

## Switching channels

The application ID, signing certificate and Android version-code ordering all
matter. Switching from an ABI APK to a universal APK or Play at the same
release can be a downgrade because ABI APKs add 6–9 to the base code. Wait for
a release with a greater version code rather than uninstalling to change
channels. Export important configuration before any deliberate uninstall.

## Official references

- [Play app signing and distribution outside Play](https://support.google.com/googleplay/android-developer/answer/9842756)
- [Obtainium import links](https://wiki.obtainium.imranr.dev/deep_links/)
