#!/usr/bin/env bash
set -euo pipefail

TSI_ROOT="$(cd "\${1:-.}" && pwd)"
AMETHYST_ROOT="$(cd "\${2:-.build/Amethyst-Android}" && pwd)"
APP_ROOT="$AMETHYST_ROOT/app_pojavlauncher"
TSI_JAVA_ROOT="$TSI_ROOT/app/src/main/java/com/tsi/launcher"

test -d "$APP_ROOT/src/main/java/net/kdt/pojavlaunch"
test -d "$TSI_JAVA_ROOT"
test -f "$AMETHYST_ROOT/LICENSE"
test -f "$APP_ROOT/src/main/AndroidManifest.xml"

python3 - "$AMETHYST_ROOT" <<'PY'
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
root_gradle = root / "build.gradle"
app_gradle = root / "app_pojavlauncher/build.gradle"
manifest_path = root / "app_pojavlauncher/src/main/AndroidManifest.xml"

def replace_once(text: str, old: str, new: str, description: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one {description}; found {count}")
    return text.replace(old, new, 1)

text = root_gradle.read_text()
text = replace_once(
    text,
    "    id 'com.android.library' version '9.3.1' apply false",
    "    id 'com.android.library' version '9.3.1' apply false\n"
    "    id 'org.jetbrains.kotlin.android' version '2.2.21' apply false\n"
    "    id 'org.jetbrains.kotlin.plugin.compose' version '2.2.21' apply false",
    "root Kotlin plugin insertion",
)
root_gradle.write_text(text)

text = app_gradle.read_text()
text = replace_once(
    text,
    "plugins {\n    id 'com.android.application' version '9.3.1'\n}",
    "plugins {\n"
    "    id 'com.android.application' version '9.3.1'\n"
    "    id 'org.jetbrains.kotlin.android'\n"
    "    id 'org.jetbrains.kotlin.plugin.compose'\n"
    "}",
    "Amethyst app plugin block",
)
text = replace_once(
    text,
    '        applicationId "org.angelauramc.amethyst"',
    '        applicationId "com.tsi.launcher"',
    "Amethyst applicationId",
)
text = replace_once(
    text,
    "        minSdkVersion 21",
    "        minSdkVersion 26",
    "TSI minimum Android API",
)
text = replace_once(
    text,
    "    buildFeatures {\n        prefab true\n        buildConfig true\n    }",
    "    buildFeatures {\n        prefab true\n        buildConfig true\n        compose true\n    }\n\n"
    "    kotlinOptions {\n        jvmTarget = '1.8'\n    }",
    "Amethyst Compose build features",
)

for old, new, label in [
    ('resValue "string", "app_name", "Amethyst (Debug)"',
     'resValue "string", "app_name", "TSI Launcher (Debug)"', "debug app name"),
    ('resValue "string", "app_short_name", "Amethyst (Debug)"',
     'resValue "string", "app_short_name", "TSI Launcher"', "debug short name"),
    ("resValue 'string', 'application_package', 'org.angelauramc.amethyst.debug'",
     "resValue 'string', 'application_package', 'com.tsi.launcher.debug'", "debug app package"),
    ("resValue 'string', 'storageProviderAuthorities', 'org.angelauramc.amethyst.scoped.gamefolder.debug'",
     "resValue 'string', 'storageProviderAuthorities', 'com.tsi.launcher.scoped.gamefolder.debug'", "debug storage authority"),
    ("resValue 'string', 'shareProviderAuthority', 'org.angelauramc.amethyst.scoped.controlfolder.debug'",
     "resValue 'string', 'shareProviderAuthority', 'com.tsi.launcher.scoped.controlfolder.debug'", "debug share authority"),
    ('resValue "string", "app_name", "Amethyst"',
     'resValue "string", "app_name", "TSI Launcher"', "release app name"),
    ('resValue "string", "app_short_name", "Amethyst"',
     'resValue "string", "app_short_name", "TSI Launcher"', "release short name"),
    ("resValue 'string', 'storageProviderAuthorities', 'org.angelauramc.amethyst.scoped.gamefolder'",
     "resValue 'string', 'storageProviderAuthorities', 'com.tsi.launcher.scoped.gamefolder'", "release storage authority"),
    ("resValue 'string', 'application_package', 'org.angelauramc.amethyst'",
     "resValue 'string', 'application_package', 'com.tsi.launcher'", "release app package"),
]:
    text = replace_once(text, old, new, label)

dependency_marker = '    implementation project(":androidnsbypass")'
compose_dependencies = (
    dependency_marker + "\n\n"
    "    // TSI Launcher Compose UI and existing Java installation/controller code.\n"
    '    implementation platform("androidx.compose:compose-bom:2025.08.00")\n'
    '    implementation "androidx.activity:activity-compose:1.10.1"\n'
    '    implementation "androidx.compose.animation:animation"\n'
    '    implementation "androidx.compose.material:material-icons-extended"\n'
    '    implementation "androidx.compose.ui:ui"\n'
    '    implementation "androidx.compose.ui:ui-tooling-preview"\n'
    '    implementation "androidx.compose.foundation:foundation"\n'
    '    implementation "androidx.compose.material3:material3"\n'
    '    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2"\n'
    '    implementation "org.apache.commons:commons-compress:1.28.0"\n'
    '    implementation "org.tukaani:xz:1.10"\n'
    '    debugImplementation "androidx.compose.ui:ui-tooling"'
)
text = replace_once(text, dependency_marker, compose_dependencies, "Amethyst dependency insertion")
app_gradle.write_text(text)

manifest = manifest_path.read_text()
test_marker = '<activity\n            android:name=".TestStorageActivity"'
start = manifest.find(test_marker)
if start < 0:
    raise SystemExit("Could not find Amethyst TestStorageActivity block")
end = manifest.find("</activity>", start)
if end < 0:
    raise SystemExit("TestStorageActivity closing tag is missing")
end += len("</activity>")
block = manifest[start:end]
block, removed = re.subn(r'\s*<intent-filter>[\s\S]*?</intent-filter>', '', block, count=1)
if removed != 1:
    raise SystemExit("Could not remove TestStorageActivity launcher filter")
manifest = manifest[:start] + block + manifest[end:]

launcher_activity = (
    '        <activity\n'
    '            android:name="com.tsi.launcher.MainActivity"\n'
    '            android:exported="true"\n'
    '            android:label="TSI Launcher">\n'
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.MAIN" />\n'
    '                <category android:name="android.intent.category.LAUNCHER" />\n'
    '            </intent-filter>\n'
    '        </activity>\n'
)
marker = '        <activity\n            android:name=".MissingStorageActivity"'
manifest = replace_once(manifest, marker, launcher_activity + marker, "TSI launcher activity insertion")

manifest = replace_once(
    manifest,
    '    <queries>\n',
    '    <queries>\n        <package android:name="com.mojang.minecraftpe" />\n',
    "Bedrock package visibility declaration",
)
manifest_path.write_text(manifest)
PY

mkdir -p "$APP_ROOT/src/main/java/com/tsi/launcher"
cp -a "$TSI_JAVA_ROOT/." "$APP_ROOT/src/main/java/com/tsi/launcher/"

mkdir -p "$APP_ROOT/src/main/assets/licenses"
cp "$AMETHYST_ROOT/LICENSE" "$APP_ROOT/src/main/assets/licenses/AMETHYST-LGPL-3.0.txt"

cat > "$APP_ROOT/src/main/assets/TSI_ENGINE_NOTICES.txt" <<'EOF'
TSI Launcher Java Edition engine integration
Engine: AngelAuraMC Amethyst-Android
Source: https://github.com/AngelAuraMC/Amethyst-Android
Pinned upstream revision: 28d6e12d68121b7dd75f2e841058a6b7cdfe4a29
License: GNU Lesser General Public License v3.0 (LGPL-3.0)
The Amethyst source tree, JNI code, renderer/input bridge, and third-party component notices are retained by the engine integration build.
See licenses/AMETHYST-LGPL-3.0.txt and the upstream source repository for license and dependency notices.
EOF

echo "TSI Compose launcher injected into Amethyst Android engine project."
