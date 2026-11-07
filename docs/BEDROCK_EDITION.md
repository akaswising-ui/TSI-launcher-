# TSI Launcher: Bedrock Edition

## Android platform behavior

The official Android Bedrock release and Beta track use the same application package, `com.mojang.minecraftpe`. Joining Android Beta updates/replaces the normal client. It is not possible to detect a separate Beta install through PackageManager, and the active Play testing enrollment is not exposed to other apps. TSI therefore displays the last channel choice made inside TSI as a preference, never as verified enrollment.

The separate Minecraft Preview app flow is not offered as a separately installed Android client in Mojang's mobile instructions. On Android, use the in-place Beta testing track. TSI's Preview card is informational and does not invent a Preview package or claim it is installed.

Official reference: https://help.minecraft.net/hc/en-us/articles/4423653831821

## Architecture

- UI: Jetpack Compose Material 3. The version browser uses `LazyColumn`, filter chips, the launcher search field, install status, action cards and a local history list.
- Install detection: `BedrockIntegration.readInstallStatus` queries `com.mojang.minecraftpe` using PackageManager and reads package name, version name, version code, first install time and last update time.
- Launch: Stable and Beta both launch the same installed Android package. The app does not pretend that Beta is a second install.
- Track actions: Stable opens the official Google Play listing. Beta opens the official Google Play testing enrollment page. Preview opens Mojang's mobile Beta/Preview information.
- Track state: `BedrockVersionHistoryStore` stores the user's last selected track. This is a user preference, not verified Google Play membership.
- Version history: the store records a local `VERSION_OBSERVED` entry when the installed package's versionName/versionCode changes, plus `TRACK_SELECTED` entries when the user changes channel preference. It retains the newest 40 events in SharedPreferences.
- Data: `BedrockVersionCatalog` fetches `bedrock/versions.json` over HTTPS. The parser validates unique IDs, channel values and HTTPS host allowlists. Built-in official links are used if the remote catalogue is unavailable.
- Distribution: Google Play handles download, package signature verification, installation and update. TSI does not mirror or redistribute proprietary game APKs.

## JSON schema

Each entry has:

| Field | Type | Meaning |
|---|---|---|
| id | string | Stable unique key |
| name | string | Display title |
| channel | string | vanilla, beta, preview |
| badge | string | Short release badge |
| versionLabel | string | Platform/channel detail |
| sizeLabel | string | Store-managed size information |
| description | string | Explanation of the channel |
| actionLabel | string | Button text |
| actionUrl | HTTPS URL | Must use play.google.com, help.minecraft.net or www.minecraft.net |
| enabled | boolean | Whether the information/action is active |

## Known limitations

1. PackageManager can read the installed app version but cannot determine whether the Play account is enrolled in Android Beta.
2. Stable and Beta cannot run side-by-side on Android because Beta replaces the same Minecraft app.
3. Android does not get a separate Preview app package in Mojang's documented mobile flow.
4. The version history is local to this device and starts recording from the first TSI detection after this feature is installed. It cannot reconstruct updates that happened before TSI observed them.
5. TSI does not provide unofficial downgrade APKs or claim support for historical Bedrock snapshots.

## Production follow-ups

1. Add automated unit tests for history de-duplication, retention, malformed JSON and catalog URL validation.
2. Add a configurable signed catalogue service, ETag cache validation and schema versioning.
3. Add a user-triggered history export/import format for device migration.
