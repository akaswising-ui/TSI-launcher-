# TSI Launcher

TSI Launcher is an Android launcher project for one dashboard that manages Minecraft Java Edition data and integrates with the official Minecraft Bedrock Android app.

## Project rule: preserve existing work

New modules are added alongside existing project files. The initial repository contained only its Android `.gitignore`; this setup does not delete or replace it.

## Current implementation

- Kotlin Android application with Jetpack Compose.
- Java Edition tab fetches the official Mojang version manifest and lists every manifest entry returned by the endpoint.
- Filters: Release, Snapshot, Old Alpha and Old Beta.
- Local profile-name field (not Microsoft authentication).
- Client JAR download for a selected Java version, with official-host allowlisting and SHA-1/size verification.
- Per-version metadata folders and separate per-version game directories.
- Bedrock tab detects the installed official Android package, displays its installed version and opens it through Android's package launcher API.
- Settings UI stores a requested RAM limit and preferred Java runtime line.

## Important scope boundary

This first milestone is a **launcher foundation**, not yet a complete Minecraft execution engine.

A Java client JAR by itself is not a playable installation. A complete Java installation must also resolve the version JSON, libraries, platform-specific native libraries, asset indexes, assets, launch arguments, authentication and the renderer/input integration. Android cannot run a normal desktop Minecraft JAR by calling `java -jar` from the app. It needs an Android-compatible JVM build, compatible LWJGL/native libraries and an Android surface/input bridge.

The next Java engine integration target should be the maintained Amethyst Android codebase, the successor project identified by the PojavLauncher repository. It should be integrated as a separate runtime/engine module or adapter after reviewing its current license, source layout, build requirements and native dependencies. Do not copy files blindly or remove license notices.

Bedrock Edition for Android is proprietary. TSI therefore detects and launches the official installed app; it does not host, patch, downgrade, or redistribute Bedrock APKs or game data. Android package signatures and package-manager rules prevent a third-party launcher from silently replacing another app's installed signed package.

## Storage layout

The app stores files under its app-specific external files directory (or internal app files directory if external app-specific storage is unavailable):

```text
.minecraft/
  versions/
    1.20.4/
      1.20.4.json
      1.20.4.jar
    1.8.9/
      1.8.9.json
      1.8.9.jar
  instances/
    1.20.4/
      game/
        saves/
        resourcepacks/
        screenshots/
        mods/
        config/
        logs/
    1.8.9/
      game/
        saves/
        resourcepacks/
        screenshots/
        mods/
        config/
        logs/
  assets/
  libraries/
```

Each version receives a distinct game directory. This prevents saves, resource packs, options, mods and configs from colliding. Large immutable assets and libraries can be shared when their checksums and paths match.

App-specific storage does not require broad storage permissions, but Android removes it when TSI is uninstalled. A future export/import feature should use the Storage Access Framework so users can back up worlds and instances.

## Planned Java launch pipeline

1. Fetch manifest and selected version JSON.
2. Resolve the required Java major version from `javaVersion.majorVersion`.
3. Download client JAR, libraries and asset index; verify supplied SHA-1 values and expected sizes.
4. Download only the libraries applicable to Android's OS and CPU architecture.
5. Extract native libraries into a version/runtime-specific native directory.
6. Build the classpath and version-specific JVM arguments from the metadata; account for the LWJGL generation and the Android renderer bridge.
7. Mount the selected instance's game directory as `gameDirectory`.
8. Start the embedded Android JVM through the engine bridge, passing the correct arguments, OpenGL/Vulkan backend, surface and input callbacks.

Do not assume a single set of JVM arguments or a single LWJGL build works for every historical version. Older releases and modern releases have different library/native requirements.

## Authentication and profiles

The current profile field is explicitly a local profile label. It is not a Microsoft login and must not be presented as an authenticated Mojang/Microsoft identity.

A production online-login implementation must use the supported Microsoft/Xbox/Minecraft authentication flow, use secure browser-based OAuth with PKCE where applicable, protect tokens using Android Keystore-backed storage, and never ask the user to type a Microsoft password into a TSI password field. Offline profiles must be clearly labelled and limited to what the selected game/server permits.

## Build

Requirements:
- Android Studio with Android SDK Platform 36.
- JDK 17 for the Android build.
- Gradle 8.13 or a compatible Gradle installation.

Open this repository in Android Studio and sync Gradle, then run the `app` configuration on an Android device/emulator.

From a machine with Gradle installed:

```bash
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Dependencies

- Kotlin Android plugin and Compose compiler plugin.
- Jetpack Compose BOM `2026.09.00`, Material 3 and Activity Compose.
- Kotlin Coroutines for network work away from the UI thread.

## Security and compatibility notes

- Only HTTPS Mojang/Minecraft download hosts (plus the historical official Minecraft S3 path) are accepted by the initial client downloader.
- Client JAR checksum and byte-size metadata are checked before the file is committed.
- Version IDs are sanitized and canonical paths are checked before creating directories.
- The manifest is a catalogue, not a guarantee that every historical asset or library is still downloadable.
- Package visibility is limited to `com.mojang.minecraftpe`; TSI does not request broad `QUERY_ALL_PACKAGES` visibility.
- RAM allocation is a requested heap limit, not a promise that a device can run a particular Minecraft version.
