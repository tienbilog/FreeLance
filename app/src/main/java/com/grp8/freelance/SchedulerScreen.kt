package com.grp8.freelance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grp8.freelance.ui.theme.*
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.*

/** Shared date format used across every tab. */
val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("Optimizer", Icons.Default.WorkOutline),
    Tab("Schedule",  Icons.Default.CalendarMonth),
    Tab("To-Do",     Icons.Default.CheckCircle),
    Tab("Income",    Icons.Default.AttachMoney)
)

/**
 * Top-level tab host for the four phases of the app:
 *   0. Optimizer — add potential projects, weigh income vs. time (Phase 1)
 *   1. Schedule  — run the optimizer, preview, and accept a suggested plan (Phase 2)
 *   2. To-Do     — the committed schedule; log hours, mark done, rebalance pace (Phase 3)
 *   3. Income    — monthly earnings from completed projects (Phase 4)
 */
@Composable
fun SchedulerApp(
    viewModel: SchedulerViewModel,
    username: String?,
    onSignOut: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val scheduledCount = allProjects.count { it.status == ProjectStatus.SCHEDULED }

    Scaffold(
        containerColor = White,
        bottomBar = {
            NavigationBar(containerColor = White, tonalElevation = 0.dp) {
                TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        icon     = {
                            if (index == 2 && scheduledCount > 0) {
                                BadgedBox(badge = { Badge { Text("$scheduledCount") } }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label, fontFamily = InterFamily, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = AccentBlue,
                            selectedTextColor   = AccentBlue,
                            indicatorColor      = AccentBlueSoft,
                            unselectedIconColor = SlateDeep,
                            unselectedTextColor = SlateDeep
                        )
                    )
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.padding(bottom = padding.calculateBottomPadding())
        ) {
            when (selectedTab) {
                0 -> OptimizerScreen(viewModel, username, onSignOut)
                1 -> ScheduleScreen(viewModel, onAccepted = { selectedTab = 2 })
                2 -> ToDoScreen(viewModel)
                3 -> IncomeScreen(viewModel)
            }
        }
    }
}