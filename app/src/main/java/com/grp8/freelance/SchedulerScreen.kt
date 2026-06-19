package com.grp8.freelance

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grp8.freelance.ui.components.AppBottomNavigation
import com.grp8.freelance.ui.components.SheetEdge
import com.grp8.freelance.ui.components.SheetShape
import com.grp8.freelance.ui.components.UtilityDrawer
import java.time.format.DateTimeFormatter

/** Shared date format used across every tab. */
val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * Top-level tab host for the four phases of the app:
 *   0. Optimizer — add potential projects, weigh income vs. time (Phase 1)
 *   1. Schedule  — run the optimizer, preview, and accept a suggested plan (Phase 2)
 *   2. To-Do     — the committed schedule; log hours, mark done, rebalance pace (Phase 3)
 *   3. Income    — monthly earnings from completed projects (Phase 4)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerApp(
    viewModel: SchedulerViewModel,
    username: String?,
    onSignOut: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var isDrawerVisible by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("FreeLance", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                    navigationIcon = {
                        IconButton(onClick = { isDrawerVisible = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                AppBottomNavigation(
                    selectedIndex = selectedTab,
                    onItemSelected = { selectedTab = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(bottom = padding.calculateBottomPadding(), top = padding.calculateTopPadding()).fillMaxSize()) {
                when (selectedTab) {
                    0 -> OptimizerScreen(viewModel, username, onSignOut) // We might refactor out onSignOut from here since it's in the drawer
                    1 -> ScheduleScreen(viewModel, onAccepted = { selectedTab = 2 })
                    2 -> ToDoScreen(viewModel)
                    3 -> IncomeScreen(viewModel)
                }
            }
        }

        // Overlay Drawer on top of Scaffold
        UtilityDrawer(
            edge = SheetEdge.LEFT,
            shapeVariant = SheetShape.Default,
            isVisible = isDrawerVisible,
            onDismiss = { isDrawerVisible = false },
            username = username,
            onLogout = onSignOut
        )
    }
}