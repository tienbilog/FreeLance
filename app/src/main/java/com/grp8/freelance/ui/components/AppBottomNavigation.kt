package com.grp8.freelance.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

data class BottomNavItem(
    val title: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem("Optimizer", Icons.Filled.AutoAwesome),
    BottomNavItem("Schedule", Icons.Filled.CalendarMonth),
    BottomNavItem("To-Do", Icons.Filled.TaskAlt),
    BottomNavItem("Income", Icons.Filled.MonetizationOn)
)

@Composable
fun AppBottomNavigation(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    var indicatorOffset by remember { mutableFloatStateOf(0f) }
    var indicatorWidth by remember { mutableFloatStateOf(0f) }
    
    val animatedOffset by animateFloatAsState(targetValue = indicatorOffset, label = "offset")
    val animatedWidth by animateFloatAsState(targetValue = indicatorWidth, label = "width")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            bottomNavItems.forEachIndexed { index, item ->
                val selected = selectedIndex == index

                NavigationBarItem(
                    selected = selected,
                    onClick = { onItemSelected(index) },
                    icon = { Icon(item.icon, contentDescription = item.title) },
                    label = { Text(item.title, style = MaterialTheme.typography.labelMedium) },
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.onGloballyPositioned { coords ->
                        if (selected) {
                            indicatorOffset = coords.positionInParent().x
                            indicatorWidth = coords.size.width.toFloat()
                        }
                    }
                )
            }
        }
        
        // Sliding indicator line at the bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset(animatedOffset.toInt(), 0) }
                .width(with(LocalDensity.current) { animatedWidth.toDp() })
                .height(3.dp)
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AppBottomNavigationPreview() {
    MaterialTheme {
        var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
        AppBottomNavigation(
            selectedIndex = selectedIndex,
            onItemSelected = { selectedIndex = it },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
