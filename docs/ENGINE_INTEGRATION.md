# TSI Android Java Engine Integration

## Pinned engine

- Engine project: [AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android)
- Pinned source commit: 28d6e12d68121b7dd75f2e841058a6b7cdfe4a29
- Upstream license: LGPL-3.0. A copy is packaged in the engine APK at assets/licenses/AMETHYST-LGPL-3.0.txt.
- The engine checkout retains its original source files, native code, submodules, license and dependency notices.

The engine is integrated as a separate Android build layer. TSI's Compose source is copied into the Amethyst Android app module at build time; the upstream engine source is not renamed, flattened into fake Java classes, or replaced with a desktop java -jar runner.

## Components used by the launch path

1. TSI's existing JavaGameInstaller downloads the selected version metadata, client JAR, permitted library artifacts, asset index and asset objects into the TSI .minecraft directory.
2. TSI's existing AndroidJreInstaller installs and verifies Android OpenJDK builds by ABI and Java major version.
3. The runtime installer now uses Amethyst's actual Tools.MULTIRT_HOME location: <app data>/runtimes. Runtime detection also checks and migrates older TSI runtime folders without deleting their source.
4. AmethystEngineBridge checks for the engine classes and libpojavexec.so, checks that both launchers resolve to the same Minecraft storage root, selects the required JRE and prepares a local Amethyst profile that points to the TSI version's isolated game directory.
5. Before launch, the bridge calls Amethyst's AsyncAssetManager.unpackSingleFiles and unpackComponents, then waits for the bundled LWJGL 3.3.3 / 3.4.1 Java components and Android ABI native bundles to become available.
6. The bridge opens Amethyst's in-game net.kdt.pojavlaunch.MainActivity with the selected version ID. Amethyst owns JVM startup through VMLauncher.launchJVM / JREUtils.launchJavaVM, Java environment setup, LWJGL 2 compatibility via LWJGLX, LWJGL 3, renderer selection, SDL/surface lifecycle, OpenAL and Android touch/gamepad input.

The VM Controller's Start button is connected to this sequence. It remains blocked when preflight detects missing version data, JRE, engine classes or native components.

## Storage compatibility

TSI's .minecraft game-data root is the same app-specific external files root used by Amethyst's Tools.DIR_GAME_HOME/.minecraft. The runtime is stored in Amethyst's private Tools.MULTIRT_HOME directory. The launch profile uses:

- version metadata/client: .minecraft/versions/<version>/<version>.json|jar
- isolated world/config directory: .minecraft/instances/<version>/game
- Java runtime: <app data>/runtimes/TSI-External-<major>

The bridge refuses to launch if the app's resolved Minecraft data roots differ, rather than silently running a stale or duplicated installation.

## Graphics, audio and controls

TSI does not implement a second renderer. Amethyst's Android renderer and compatibility layers are used:

- LWJGL 2 games use the engine's LWJGLX compatibility path.
- LWJGL 3 games use the matching bundled 3.3.3 or 3.4.1 components.
- Android native .so files are packaged for the supported ABI by Amethyst's AAR/native build.
- Amethyst's SDL and MinecraftGLSurface handle display surface lifecycle.
- Amethyst's OpenAL-Soft path supplies game audio.
- Amethyst's touch, virtual mouse, keyboard and gamepad input bridge dispatches Android input to Minecraft.

The selected renderer and the exact Java version still depend on device ABI/GPU support and Minecraft metadata. A successful APK build does not prove that every version will run on every handset.

## Authentication boundary

The initial bridge creates a clearly local/offline profile to validate game startup. It does not pretend that the profile is a Microsoft-authenticated account. Microsoft/Xbox/Minecraft entitlement authentication must be integrated before online-account play is advertised.

## Bedrock boundary

Bedrock remains a separate official Android app integration. TSI detects and launches the installed com.mojang.minecraftpe app. It does not download, modify, downgrade or bundle proprietary Bedrock game packages.

## Build workflow

The engine-enabled APK is built by:

- Workflow: [Android Engine APK](https://github.com/akaswising-ui/TSI-launcher-/actions/workflows/android-engine.yml)
- Source: TSI main branch plus the pinned Amethyst commit above
- Toolchain: JDK 21, Android SDK 37, NDK 27.3.13750724 and Gradle 9.6.1
- Output artifact: TSI-Launcher-engine-debug
- Direct release asset: TSI-Launcher-engine-debug.apk

The older android.yml standalone Compose build is retained as a lightweight UI/foundation build. It does not contain Amethyst JNI/native engine components and must not be treated as the playable engine APK.
