package com.grp8.freelance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter

/** Shared date format used across the input and results screens. */
val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * Top-level router: shows either the project input screen or the results
 * screen, depending on whether a schedule has been computed.
 */
@Composable
fun SchedulerApp(
    viewModel: SchedulerViewModel,
    username: String?,
    onSignOut: () -> Unit
) {
    val result by viewModel.result.collectAsStateWithLifecycle()

    var projectToEdit by remember { mutableStateOf<Project?>(null) }

    if (result != null) {
        ResultsScreen(
            result = result!!,
            onBack = { viewModel.clearResult() },
            onEditProject = { project ->
                viewModel.clearResult()
                projectToEdit = project
            }
        )
    } else {
        InputScreen(
            viewModel           = viewModel,
            initialEditProject  = projectToEdit,
            onEditHandled       = { projectToEdit = null },
            username            = username,
            onSignOut           = onSignOut
        )
    }
}