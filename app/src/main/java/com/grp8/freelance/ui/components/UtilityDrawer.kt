package com.grp8.freelance.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SheetEdge {
    LEFT, RIGHT, BOTTOM
}

enum class SheetShape {
    Default, Straight, Inset
}

@Composable
fun UtilityDrawer(
    edge: SheetEdge = SheetEdge.LEFT,
    shapeVariant: SheetShape = SheetShape.Default,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    username: String? = null,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val drawerWidthDp = 300.dp
    val drawerWidthPx = with(density) { drawerWidthDp.toPx() }

    val coroutineScope = rememberCoroutineScope()
    
    // Offset for animation and dragging
    val offset = remember { Animatable(
        initialValue = when (edge) {
            SheetEdge.LEFT -> -drawerWidthPx
            SheetEdge.RIGHT -> screenWidthPx
            SheetEdge.BOTTOM -> screenHeightPx
        }
    ) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            offset.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    suspend fun dismissDrawer() {
        offset.animateTo(
            targetValue = when (edge) {
                SheetEdge.LEFT -> -drawerWidthPx
                SheetEdge.RIGHT -> drawerWidthPx 
                SheetEdge.BOTTOM -> screenHeightPx
            },
            animationSpec = tween(300)
        )
        onDismiss()
    }

    val dragEndThreshold = when (edge) {
        SheetEdge.LEFT -> -drawerWidthPx / 3
        SheetEdge.RIGHT -> drawerWidthPx / 3
        SheetEdge.BOTTOM -> screenHeightPx / 3
    }

    val shape: Shape = when (shapeVariant) {
        SheetShape.Default -> when (edge) {
            SheetEdge.BOTTOM -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            SheetEdge.LEFT -> RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            SheetEdge.RIGHT -> RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
        }
        SheetShape.Straight -> RoundedCornerShape(0.dp)
        SheetShape.Inset -> RoundedCornerShape(16.dp)
    }
    
    val insetPadding = if (shapeVariant == SheetShape.Inset) 16.dp else 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(onClick = { coroutineScope.launch { dismissDrawer() } })
    ) {
        val velocityTracker = remember { VelocityTracker() }

        Box(
            modifier = Modifier
                .align(
                    when (edge) {
                        SheetEdge.LEFT -> Alignment.CenterStart
                        SheetEdge.RIGHT -> Alignment.CenterEnd
                        SheetEdge.BOTTOM -> Alignment.BottomCenter
                    }
                )
                .offset {
                    when (edge) {
                        SheetEdge.LEFT, SheetEdge.RIGHT -> IntOffset(offset.value.roundToInt(), 0)
                        SheetEdge.BOTTOM -> IntOffset(0, offset.value.roundToInt())
                    }
                }
                .padding(insetPadding)
                .clickable(enabled = false) {} // Consume clicks inside drawer so they don't dismiss
                .pointerInput(edge) {
                    detectDragGestures(
                        onDragStart = { velocityTracker.resetTracking() },
                        onDragEnd = {
                            val velocity = if (edge == SheetEdge.BOTTOM) {
                                velocityTracker.calculateVelocity().y
                            } else {
                                velocityTracker.calculateVelocity().x
                            }
                            
                            coroutineScope.launch {
                                val shouldDismiss = when (edge) {
                                    SheetEdge.LEFT -> offset.value < dragEndThreshold || velocity < -500f
                                    SheetEdge.RIGHT -> offset.value > dragEndThreshold || velocity > 500f
                                    SheetEdge.BOTTOM -> offset.value > dragEndThreshold || velocity > 500f
                                }
                                if (shouldDismiss) {
                                    dismissDrawer()
                                } else {
                                    offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            velocityTracker.addPointerInputChange(change)
                            change.consume()
                            coroutineScope.launch {
                                val newOffset = offset.value + if (edge == SheetEdge.BOTTOM) dragAmount.y else dragAmount.x
                                val constrainedOffset = when (edge) {
                                    SheetEdge.LEFT -> newOffset.coerceAtMost(0f)
                                    SheetEdge.RIGHT -> newOffset.coerceAtLeast(0f)
                                    SheetEdge.BOTTOM -> newOffset.coerceAtLeast(0f)
                                }
                                offset.snapTo(constrainedOffset)
                            }
                        }
                    )
                }
                .then(
                    if (edge == SheetEdge.BOTTOM) Modifier.fillMaxWidth().wrapContentHeight()
                    else Modifier.width(drawerWidthDp).fillMaxHeight()
                )
                .background(MaterialTheme.colorScheme.surface, shape)
                .padding(top = 16.dp, bottom = 16.dp)
        ) {
            // Layout content based on edge (drag pill orientation)
            if (edge == SheetEdge.BOTTOM) {
                Column(modifier = Modifier.fillMaxSize()) {
                    DragPill(horizontal = true, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(modifier = Modifier.height(16.dp))
                    DrawerContent(username = username, onLogout = { coroutineScope.launch { dismissDrawer(); onLogout() } }, modifier = Modifier.weight(1f))
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (edge == SheetEdge.RIGHT) {
                        Box(modifier = Modifier.fillMaxHeight().padding(start = 8.dp), contentAlignment = Alignment.Center) {
                            DragPill(horizontal = false)
                        }
                    }
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        DrawerContent(username = username, onLogout = { coroutineScope.launch { dismissDrawer(); onLogout() } }, modifier = Modifier.fillMaxSize())
                    }
                    if (edge == SheetEdge.LEFT) {
                        Box(modifier = Modifier.fillMaxHeight().padding(end = 8.dp), contentAlignment = Alignment.Center) {
                            DragPill(horizontal = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DragPill(horizontal: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(if (horizontal) 40.dp else 4.dp, if (horizontal) 4.dp else 40.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
    )
}

@Composable
fun DrawerContent(username: String?, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) { Text("Log Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Group 1: Account & Security
            DrawerGroup("Account & Security")
            DrawerItem("Account Settings", Icons.Default.Person)
            DrawerItem("Security & Privacy", Icons.Default.Lock)
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Group 2: App Performance & Data
            DrawerGroup("App Performance & Data")
            DrawerItem("Optimizer Preferences", Icons.Default.Tune)
            DrawerItem("Data Sync & Export", Icons.Default.CloudUpload)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Group 3: Support & Feedback
            DrawerGroup("Support & Feedback")
            DrawerItem("Help & Support", Icons.Default.HelpOutline)
            DrawerItem("App Feedback", Icons.Default.RateReview)
        }

        Spacer(Modifier.height(16.dp))

        // Bottom Profile & Logout Section
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = username?.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = username ?: "Guest",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (username != null) "Pro Account" else "Guest Account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLogoutDialog = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Logout", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun DrawerGroup(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun DrawerItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Preview(showBackground = true)
@Composable
fun UtilityDrawerPreview() {
    MaterialTheme {
        var isVisible by remember { mutableStateOf(true) }
        UtilityDrawer(
            edge = SheetEdge.LEFT,
            shapeVariant = SheetShape.Inset,
            isVisible = isVisible,
            onDismiss = { isVisible = false }
        )
    }
}
