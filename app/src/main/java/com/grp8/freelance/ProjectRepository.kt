package com.grp8.freelance

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore(name = "projects")

class ProjectRepository(private val context: Context) {

    private val gson = Gson()
    private val KEY = stringPreferencesKey("project_list")

    val projectsFlow: Flow<List<Project>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY] ?: return@map emptyList()
        val type = object : TypeToken<List<ProjectJson>>() {}.type
        val list: List<ProjectJson> = gson.fromJson(json, type)
        list.map { it.toProject() }
    }

    suspend fun save(projects: List<Project>) {
        val json = gson.toJson(projects.map { it.toJson() })
        context.dataStore.edit { prefs -> prefs[KEY] = json }
    }

    // Gson can't serialize LocalDate, so we use a simple bridge data class
    private data class ProjectJson(
        val id: Int,
        val name: String,
        val clientName: String,
        val deadlineDate: String,
        val hoursNeeded: Double,
        val ratePerHour: Int
    )

    private fun Project.toJson() = ProjectJson(id, name, clientName, deadlineDate.toString(), hoursNeeded, ratePerHour)
    private fun ProjectJson.toProject() = Project(id, name, clientName, LocalDate.parse(deadlineDate), hoursNeeded, ratePerHour)
}