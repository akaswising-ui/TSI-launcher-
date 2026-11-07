package com.tsi.launcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class PackSection(val title: String) {
    JAVA("Java Edition"),
    MODDING("Modding")
}

private data class PackPreview(
    val id: String,
    val title: String,
    val category: String,
    val version: String,
    val description: String,
    val accent: Color,
    val second: Color,
    val icon: String
)

private val packPreviews = listOf(
    PackPreview(
        "java-performance", "Performance Essentials", "Java Mod Pack", "Preview",
        "A sample collection for performance-focused Java setups.",
        Color(0xFF4B9470), Color(0xFFB6D99A), "FPS"
    ),
    PackPreview(
        "java-adventure", "Adventure Biomes", "Java Mod Pack", "Preview",
        "A sample world-building and exploration collection.",
        Color(0xFF397D8D), Color(0xFF9CD4C6), "BIO"
    ),
    PackPreview(
        "java-builders", "Builder's Collection", "Resource Pack", "Preview",
        "A sample collection for builders and creative worlds.",
        Color(0xFF9B7651), Color(0xFFE4C48C), "BLD"
    ),
    PackPreview(
        "java-visuals", "Visual Upgrade Pack", "Visual Preset", "Preview",
        "A placeholder for compatible visual enhancements.",
        Color(0xFF575A9A), Color(0xFFB6A4E6), "VFX"
    ),
remove Bedrock catalogue)

@Composable
fun ModPackManagerScreen(
    modifier: Modifier = Modifier,
    profileName: String,
    searchQuery: String,
    onInstancesClick: () -> Unit
) {
    var selectedSection by rememberSaveable { mutableStateOf(PackSection.JAVA) }
    var selectedPack by remember { mutableStateOf<PackPreview?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xFF16191A))
            .padding(12.dp)
    ) {
        val wide = maxWidth >= 720.dp
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ManagerSidebar(
                    modifier = Modifier.width(174.dp),
                    onInstancesClick = onInstancesClick
                )
                ManagerContent(
                    modifier = Modifier.weight(1f),
                    profileName = profileName,
                    searchQuery = searchQuery,
                    selectedSection = selectedSection,
                    onSectionChange = { selectedSection = it },
                    onPackClick = { selectedPack = it }
                )
            }
        } else {
            ManagerContent(
                modifier = Modifier.fillMaxSize(),
                profileName = profileName,
                searchQuery = searchQuery,
                selectedSection = selectedSection,
                onSectionChange = { selectedSection = it },
                onPackClick = { selectedPack = it }
            )
        }
    }

    selectedPack?.let { pack ->
        AlertDialog(
            onDismissRequest = { selectedPack = null },
            title = { Text(pack.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pack.description)
                    Text("Type: ${pack.category}")
                    Text(
                        "This is preview catalogue data. Real mod/add-on downloads and installation are not connected yet.",
                        color = Color(0xFF9C6A23),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPack = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun ManagerSidebar(
    modifier: Modifier,
    onInstancesClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF222526))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            "TSI LAUNCHER",
            color = Color(0xFFE8F1EC),
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(8.dp)
        )
        SidebarItem(
            title = "INSTANCES",
            icon = { Icon(Icons.Filled.Folder, null) },
            selected = false,
            onClick = onInstancesClick
        )
        SidebarItem(
            title = "MOD PACKS",
            icon = { Icon(Icons.Filled.Apps, null) },
            selected = true,
            onClick = {}
        )
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(6.dp)
        ) {
            Icon(Icons.Filled.Settings, null, tint = Color(0xFF9FACAA))
            Spacer(Modifier.width(8.dp))
            Text("Manager", color = Color(0xFF9FACAA), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SidebarItem(
    title: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        if (selected) Color(0xFF3B4842) else Color.Transparent,
        tween(220),
        label = "manager-sidebar"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = if (selected) Color(0xFFD8F0DA) else Color(0xFFC4CDCA),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ManagerContent(
    modifier: Modifier,
    profileName: String,
    searchQuery: String,
    selectedSection: PackSection,
    onSectionChange: (PackSection) -> Unit,
    onPackClick: (PackPreview) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Unified Mod Pack Manager",
                    color = Color(0xFFF2F4F1),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Browse Java mod packs and modding resources",
                    color = Color(0xFF9CA8A3),
                    fontSize = 11.sp
                )
            }
            Surface(
                color = Color(0xFF2A3030),
                shape = RoundedCornerShape(30.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF495450))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF9ACB9C), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(profileName, color = Color(0xFFE6EDE8), fontSize = 11.sp)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFF24292A))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PackSection.entries.forEach { section ->
                val selected = selectedSection == section
                val bg by animateColorAsState(
                    if (selected) {
                        Color(0xFFE2D2D2)
                    } else Color.Transparent,
                    tween(240),
                    label = "pack-section-color"
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSectionChange(section) },
                    color = bg,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        section.title,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 11.dp),
                        color = if (selected) Color(0xFF17211B) else Color(0xFFD0D7D3),
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        val shownPacks = remember(selectedSection, searchQuery) {
            val categoryPacks = when (selectedSection) {
                PackSection.JAVA -> packPreviews.filter { it.id.startsWith("java-") }
                PackSection.MODDING -> packPreviews.filter {
                    it.id.startsWith("java-") && it.category != "Java Mod Pack"
                }
 
            }
            categoryPacks.filter {
                searchQuery.isBlank() ||
                    it.title.contains(searchQuery.trim(), ignoreCase = true) ||
                    it.category.contains(searchQuery.trim(), ignoreCase = true)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (selectedSection) {
                        PackSection.JAVA -> "Java mod packs"
                        PackSection.MODDING -> "Modding tools & resources"
 
                    },
                    color = Color(0xFFE8EFEB),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    "${shownPacks.size} preview listings",
                    color = Color(0xFF96A39D),
                    fontSize = 10.sp
                )
            }
            Surface(
                color = Color(0xFF29312D),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Visibility, null, tint = Color(0xFFB7D5AE), modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PREVIEW", color = Color(0xFFD2E9CA), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
        }

        AnimatedContent(
            targetState = selectedSection,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(
                    initialScale = 0.985f,
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                )) togetherWith
                    (fadeOut(tween(150)) + scaleOut(targetScale = 0.99f, animationSpec = tween(160)))
            },
            label = "pack-grid-transition"
        ) { target ->
            if (shownPacks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No preview listings match this search.", color = Color(0xFFA8B3AE))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 164.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shownPacks, key = { it.id }) { pack ->
                        PackPreviewCard(pack = pack, onClick = { onPackClick(pack) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PackPreviewCard(pack: PackPreview, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(240))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF292E2F)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF454D49)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .clip(RoundedCornerShape(11.dp))
            ) {
                PackArtwork(pack)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MiniTag("PREVIEW", Color(0xFF3C774F))
                    MiniTag(pack.category.uppercase(), Color(0xFFE8D7C6), darkText = true)
                }
                Text(
                    pack.icon,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(7.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                pack.title,
                color = Color(0xFFF0F3F0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                pack.description,
                color = Color(0xFFAAB5AF),
                fontSize = 10.sp,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = Color(0xFF93B99B), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "Install support not connected",
                    color = Color(0xFFB7C4BD),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(35.dp),
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9FC18E)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("VIEW DETAILS", color = Color(0xFF172117), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MiniTag(text: String, color: Color, darkText: Boolean = false) {
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            color = if (darkText) Color(0xFF242B26) else Color.White,
            fontSize = 7.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun PackArtwork(pack: PackPreview) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.linearGradient(
                    listOf(pack.accent, pack.second, pack.accent.copy(alpha = 0.88f)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )

            // Stylized block landscape, kept abstract so previews do not imply real downloaded art.
            val groundY = size.height * 0.66f
            drawRect(Color(0xFF263D31).copy(alpha = 0.68f), Offset(0f, groundY), Size(size.width, size.height - groundY))
            for (i in 0..5) {
                val x = size.width * (0.05f + i * 0.17f)
                val h = size.height * (0.18f + (i % 3) * 0.055f)
                drawRect(
                    color = Color(0xFF1C302A).copy(alpha = 0.78f),
                    topLeft = Offset(x, groundY - h),
                    size = Size(size.width * 0.095f, h)
                )
                drawRect(
                    color = Color(0xFF8CC58B).copy(alpha = 0.9f),
                    topLeft = Offset(x - 3f, groundY - h),
                    size = Size(size.width * 0.11f, size.height * 0.045f)
                )
            }
            val diamond = Path().apply {
                moveTo(size.width * 0.50f, size.height * 0.12f)
                lineTo(size.width * 0.66f, size.height * 0.39f)
                lineTo(size.width * 0.50f, size.height * 0.55f)
                lineTo(size.width * 0.34f, size.height * 0.39f)
                close()
            }
            drawPath(diamond, Color.White.copy(alpha = 0.17f), style = Stroke(width = 2f))
        }
        Text(
            pack.title.uppercase(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 10.dp),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
