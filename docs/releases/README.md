# Release channels

The GitHub workflows build artifacts only; they do not publish a release.
Run the debug or unsigned-release workflow manually for validation. The
unsigned APKs are not distributable builds. A signed release requires the
configured CloudBridge release key.

## Publishing

For Google Play, build a signed OSS AAB with
`./gradlew bundleOssRelease -PnoAbiSplits`. For Obtainium and direct GitHub
distribution, use signed universal APKs. See the [publishing guide](../PUBLISHING.md)
for signing and store setup.

## Version codes

Update the patch version and default version code together in `app/build.gradle`.
The universal APK and Play AAB use the base version code; ABI APKs add 6–9.
Use a greater base code for each new release so installed versions can update
without requiring an uninstall.
