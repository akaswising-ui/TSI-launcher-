package com.tsi.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tsi.launcher.bedrock.BedrockIntegration
import com.tsi.launcher.data.JavaClientInstaller
import com.tsi.launcher.data.MinecraftVersion
import com.tsi.launcher.data.VersionFilter
import com.tsi.launcher.data.VersionManifestRepository
import com.tsi.launcher.storage.VersionDirectoryManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TsiLauncherApp()
        }
    }
}

private enum class LauncherTab(val title: String) {
    JAVA("Java"),
    BEDROCK("Bedrock"),
    SETTINGS("Settings")
}

@Composable
private fun TsiLauncherApp() {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("tsi_launcher_preferences", 0)
    }
    val manifestRepository = remember { VersionManifestRepository() }
    val directoryManager = remember { VersionDirectoryManager(context.applicationContext) }
    val clientInstaller = remember { JavaClientInstaller(directoryManager) }
    val scope = rememberCoroutineScope()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var versions by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }
    var selectedFilter by rememberSaveable { mutableStateOf(VersionFilter.ALL) }
    var isLoading by remember { mutableStateOf(false) }
    var activeDownload by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("Loading the official Java version list…") }
    var profileName by rememberSaveable {
        mutableStateOf(preferences.getString("profile_name", "Player") ?: "Player")
    }
    var ramMb by rememberSaveable {
        mutableFloatStateOf(preferences.getInt("ram_mb", 768).toFloat())
    }
    var javaRuntime by rememberSaveable {
        mutableStateOf(preferences.getString("java_runtime", "Auto") ?: "Auto")
    }

    suspend fun refreshVersions() {
        isLoading = true
        statusMessage = "Contacting Mojang version manifest…"
        try {
            versions = manifestRepository.fetchVersions()
            statusMessage = "Loaded ${versions.size} Java version entries from Mojang."
        } catch (error: Exception) {
            statusMessage = "Could not load versions: ${error.message ?: "network error"}"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshVersions()
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column {
                            Text("TSI Launcher", fontWeight = FontWeight.Bold)
                            Text(
                                "Java Edition + Bedrock Edition",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
                )
            },
            bottomBar = {
                NavigationBar {
                    LauncherTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Text(tab.title.take(1)) },
                            label = { Text(tab.title) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            when (LauncherTab.entries[selectedTab]) {
                LauncherTab.JAVA -> JavaEditionScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    versions = versions,
                    selectedFilter = selectedFilter,
                    onFilterChange = { selectedFilter = it },
                    loading = isLoading,
                    statusMessage = statusMessage,
                    onRefresh = { scope.launch { refreshVersions() } },
                    profileName = profileName,
                    onProfileChange = {
                        profileName = it
                        preferences.edit().putString("profile_name", it).apply()
                    },
                    activeDownload = activeDownload,
                    onDownload = { version ->
                        scope.launch {
                            activeDownload = version.id
                            statusMessage = "Downloading ${version.id} client JAR and verifying SHA-1…"
                            try {
                                val result = clientInstaller.downloadClientJar(version)
                                val javaText = result.requiredJavaMajor?.toString() ?: "not specified"
                                statusMessage =
                                    "${result.versionId} client JAR verified. Required Java: $javaText. Full game runtime is not connected yet."
                            } catch (error: Exception) {
                                statusMessage =
                                    "Download failed for ${version.id}: ${error.message ?: "unknown error"}"
                            } finally {
                                activeDownload = null
                            }
                        }
                    }
                )

                LauncherTab.BEDROCK -> BedrockEditionScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )

                LauncherTab.SETTINGS -> SettingsScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    ramMb = ramMb,
                    onRamChange = {
                        ramMb = it
                        preferences.edit().putInt("ram_mb", it.toInt()).apply()
                    },
                    javaRuntime = javaRuntime,
                    onJavaRuntimeChange = {
                        javaRuntime = it
                        preferences.edit().putString("java_runtime", it).apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun JavaEditionScreen(
    modifier: Modifier,
    versions: List<MinecraftVersion>,
    selectedFilter: VersionFilter,
    onFilterChange: (VersionFilter) -> Unit,
    loading: Boolean,
    statusMessage: String,
    onRefresh: () -> Unit,
    profileName: String,
    onProfileChange: (String) -> Unit,
    activeDownload: String?,
    onDownload: (MinecraftVersion) -> Unit
) {
    val filteredVersions = versions.filter { selectedFilter.accepts(it.type) }
    var filterMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Player profile", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = profileName,
                            onValueChange = onProfileChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Local profile name") }
                        )
                        Text(
                            "This is a local profile label, not Microsoft account authentication.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { filterMenuExpanded = true }) {
                        Text(selectedFilter.label)
                    }
                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false }
                    ) {
                        VersionFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.label) },
                                onClick = {
                                    onFilterChange(filter)
                                    filterMenuExpanded = false
                                }
                            )
                        }
                    }
                    Button(onClick = onRefresh, enabled = !loading) {
                        Text(if (loading) "Loading…" else "Refresh list")
                    }
                }
            }

            item {
                Text(
                    "Java versions: ${filteredVersions.size} shown / ${versions.size} total",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(statusMessage, style = MaterialTheme.typography.bodySmall)
            }

            if (filteredVersions.isEmpty() && !loading) {
                item {
                    Text("No versions in this filter. Refresh the manifest or choose All versions.")
                }
            }

            items(filteredVersions, key = { it.id }) { version ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(version.id, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Type: ${version.type}  •  ${version.releaseTime.take(10)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onDownload(version) },
                            enabled = activeDownload == null
                        ) {
                            Text(
                                if (activeDownload == version.id) "Downloading…"
                                else "Download client JAR"
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "The client JAR download is only one installation component. Libraries, assets, natives and the Android Java runtime are separate stages.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun BedrockEditionScreen(modifier: Modifier) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(BedrockIntegration.readInstallStatus(context)) }
    var launchMessage by remember { mutableStateOf("") }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Minecraft Bedrock for Android", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(status.message)
                Text(
                    if (status.installed) {
                        "Installed version: ${status.versionName} (code ${status.versionCode})"
                    } else {
                        "Install the official Minecraft app separately, then refresh this page."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            status = BedrockIntegration.readInstallStatus(context)
                            launchMessage = ""
                        }
                    ) {
                        Text("Check again")
                    }
                    Button(
                        enabled = status.installed,
                        onClick = {
                            try {
                                BedrockIntegration.launchInstalledGame(context)
                                launchMessage = "Opening the installed Minecraft app…"
                            } catch (error: Exception) {
                                launchMessage = error.message ?: "Could not open Minecraft"
                            }
                        }
                    ) {
                        Text("Play")
                    }
                }
                if (launchMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(launchMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(
            "TSI reads the installed app version and opens the official app. Android does not let TSI replace or downgrade another app's signed game package.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    ramMb: Float,
    onRamChange: (Float) -> Unit,
    javaRuntime: String,
    onJavaRuntimeChange: (String) -> Unit
) {
    var javaMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Java memory target", style = MaterialTheme.typography.titleMedium)
                Text("${ramMb.toInt()} MB")
                Slider(
                    value = ramMb,
                    onValueChange = onRamChange,
                    valueRange = 512f..4096f,
                    steps = 6
                )
                Text(
                    "This is a requested Java heap limit. The runtime must still respect device memory and available RAM.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Preferred Java runtime", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { javaMenuExpanded = true }) {
                    Text(javaRuntime)
                }
                DropdownMenu(
                    expanded = javaMenuExpanded,
                    onDismissRequest = { javaMenuExpanded = false }
                ) {
                    listOf("Auto", "Java 8", "Java 17", "Java 21", "Java 25").forEach { value ->
                        DropdownMenuItem(
                            text = { Text(value) },
                            onClick = {
                                onJavaRuntimeChange(value)
                                javaMenuExpanded = false
                            }
                        )
                    }
                }
                Text(
                    "The selected runtime is a preference only. TSI will validate it against the selected version metadata when the JVM engine is integrated.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
