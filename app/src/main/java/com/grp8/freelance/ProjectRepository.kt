package com.grp8.freelance

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    /** One-shot read — used only during guest-to-signed-in data migration. */
    suspend fun load(): List<Project> {
        val prefs = context.dataStore.data.first()
        val json  = prefs[KEY] ?: return emptyList()
        val type  = object : TypeToken<List<ProjectJson>>() {}.type
        val list: List<ProjectJson> = gson.fromJson(json, type)
        return list.map { it.toProject() }
    }

    suspend fun save(projects: List<Project>) {
        val json = gson.toJson(projects.map { it.toJson() })
        context.dataStore.edit { prefs -> prefs[KEY] = json }
    }

    // -------------------------------------------------------------------------
    // Serialised shape. Stored as plain strings/primitives so Gson doesn't need
    // custom adapters for the enum or nullable LocalDate fields.
    // -------------------------------------------------------------------------
    private data class ProjectJson(
        val id: Int,
        val name: String,
        val clientName: String,
        val deadlineDate: String,
        val hoursNeeded: Double,
        val rateType: String,
        val ratePerHour: Double,
        val fixedAmount: Double,
        val status: String,
        val assignedDate: String?,
        val hoursLogged: Double,
        val completedDate: String?
    )

    private fun Project.toJson() = ProjectJson(
        id            = id,
        name          = name,
        clientName    = clientName,
        deadlineDate  = deadlineDate.toString(),
        hoursNeeded   = hoursNeeded,
        rateType      = rateType.name,
        ratePerHour   = ratePerHour,
        fixedAmount   = fixedAmount,
        status        = status.name,
        assignedDate  = assignedDate?.toString(),
        hoursLogged   = hoursLogged,
        completedDate = completedDate?.toString()
    )

    private fun ProjectJson.toProject() = Project(
        id            = id,
        name          = name,
        clientName    = clientName,
        deadlineDate  = LocalDate.parse(deadlineDate),
        hoursNeeded   = hoursNeeded,
        rateType      = runCatching { RateType.valueOf(rateType) }.getOrDefault(RateType.HOURLY),
        ratePerHour   = ratePerHour,
        fixedAmount   = fixedAmount,
        status        = runCatching { ProjectStatus.valueOf(status) }.getOrDefault(ProjectStatus.POTENTIAL),
        assignedDate  = assignedDate?.let { LocalDate.parse(it) },
        hoursLogged   = hoursLogged,
        completedDate = completedDate?.let { LocalDate.parse(it) }
    )
}