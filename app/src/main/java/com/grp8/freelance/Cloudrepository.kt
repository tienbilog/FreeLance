package com.grp8.freelance

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.DayOfWeek

/**
 * Persists a signed-in user's projects in Firestore.
 *
 * Document path:  users/{uid}/data/projects
 * The document holds a single field "list" containing the serialised project array.
 *
 * This mirrors ProjectRepository's API so SchedulerViewModel can swap between
 * them without caring which one is active.
 */
class CloudRepository(private val uid: String) {

    private val db = FirebaseFirestore.getInstance()
    private val doc = db.collection("users").document(uid)
        .collection("data").document("projects")
    private val scheduleDoc = db.collection("users").document(uid)
        .collection("data").document("schedule")

    /** Real-time stream of the user's projects from Firestore. */
    val projectsFlow: Flow<List<Project>> = callbackFlow {
        val listener = doc.addSnapshotListener { snapshot, _ ->
            val raw = snapshot?.get("list")
            @Suppress("UNCHECKED_CAST")
            val maps = raw as? List<Map<String, Any>> ?: emptyList()
            trySend(maps.map { it.toProject() })
        }
        awaitClose { listener.remove() }
    }

    suspend fun save(projects: List<Project>) {
        val data = mapOf("list" to projects.map { it.toMap() })
        doc.set(data, SetOptions.merge()).await()
    }

    suspend fun load(): List<Project> {
        val snapshot = doc.get().await()
        val raw = snapshot.get("list")
        @Suppress("UNCHECKED_CAST")
        val maps = raw as? List<Map<String, Any>> ?: emptyList()
        return maps.map { it.toProject() }
    }

    val scheduleFlow: Flow<Map<DayOfWeek, Double>> = callbackFlow {
        val listener = scheduleDoc.addSnapshotListener { snapshot, _ ->
            val raw = snapshot?.get("pattern")
            @Suppress("UNCHECKED_CAST")
            val maps = raw as? Map<String, Number> ?: emptyMap()
            trySend(maps.mapKeys { runCatching { DayOfWeek.valueOf(it.key) }.getOrDefault(DayOfWeek.MONDAY) }.mapValues { it.value.toDouble() })
        }
        awaitClose { listener.remove() }
    }

    suspend fun saveSchedule(schedule: Map<DayOfWeek, Double>) {
        val data = mapOf("pattern" to schedule.mapKeys { it.key.name })
        scheduleDoc.set(data, SetOptions.merge()).await()
    }

    suspend fun loadSchedule(): Map<DayOfWeek, Double> {
        val snapshot = scheduleDoc.get().await()
        val raw = snapshot.get("pattern")
        @Suppress("UNCHECKED_CAST")
        val maps = raw as? Map<String, Number> ?: emptyMap()
        return maps.mapKeys { runCatching { DayOfWeek.valueOf(it.key) }.getOrDefault(DayOfWeek.MONDAY) }.mapValues { it.value.toDouble() }
    }

    // -------------------------------------------------------------------------
    // Serialisation helpers — plain Map<String,Any> so Firestore can store them
    // without a custom serialiser dependency. Nullable LocalDate fields are
    // stored as empty string when absent (Firestore drops null map values).
    // -------------------------------------------------------------------------
    private fun Project.toMap(): Map<String, Any> = mapOf(
        "id"            to id,
        "name"          to name,
        "clientName"    to clientName,
        "deadlineDate"  to deadlineDate.toString(),
        "hoursNeeded"   to hoursNeeded,
        "rateType"      to rateType.name,
        "ratePerHour"   to ratePerHour,
        "fixedAmount"   to fixedAmount,
        "status"        to status.name,
        "assignedDates" to assignedDates.mapKeys { it.key.toString() },
        "hoursLogged"   to hoursLogged,
        "completedDate" to (completedDate?.toString() ?: ""),
        "subtasks"      to subtasks.map { mapOf("id" to it.id, "title" to it.title, "isCompleted" to it.isCompleted) },
        "taskStatus"    to taskStatus.name,
        "scheduleWarning" to (scheduleWarning ?: ""),
        "completedAssignments" to completedAssignments.map { it.toString() }
    )

    private fun Map<String, Any>.toProject(): Project {
        fun dateOrNull(key: String): LocalDate? {
            val raw = get(key) as? String
            return if (raw.isNullOrBlank()) null else LocalDate.parse(raw)
        }

        val datesRaw = get("assignedDates") as? Map<String, Number> ?: emptyMap()
        val assignedDatesMap = datesRaw.mapKeys { LocalDate.parse(it.key) }.mapValues { it.value.toDouble() }
        
        val subtasksRaw = get("subtasks") as? List<Map<String, Any>> ?: emptyList()
        val subtasksList = subtasksRaw.map {
            Subtask(
                id = it["id"] as? String ?: "",
                title = it["title"] as? String ?: "",
                isCompleted = it["isCompleted"] as? Boolean ?: false
            )
        }
        
        val compAssigRaw = get("completedAssignments") as? List<String> ?: emptyList()
        val completedAssignmentsSet = compAssigRaw.mapNotNull { 
            runCatching { LocalDate.parse(it) }.getOrNull() 
        }.toSet()
        
        val scheduleWarnRaw = get("scheduleWarning") as? String
        val parsedScheduleWarning = if (scheduleWarnRaw.isNullOrBlank()) null else scheduleWarnRaw

        return Project(
            id            = (get("id") as? Long)?.toInt() ?: 0,
            name          = get("name") as? String ?: "",
            clientName    = get("clientName") as? String ?: "",
            deadlineDate  = LocalDate.parse(get("deadlineDate") as? String ?: LocalDate.now().toString()),
            hoursNeeded   = (get("hoursNeeded") as? Double) ?: 0.0,
            rateType      = runCatching { RateType.valueOf(get("rateType") as? String ?: "HOURLY") }
                .getOrDefault(RateType.HOURLY),
            ratePerHour   = (get("ratePerHour") as? Double) ?: 0.0,
            fixedAmount   = (get("fixedAmount") as? Double) ?: 0.0,
            status        = runCatching { ProjectStatus.valueOf(get("status") as? String ?: "POTENTIAL") }
                .getOrDefault(ProjectStatus.POTENTIAL),
            assignedDates = assignedDatesMap,
            hoursLogged   = (get("hoursLogged") as? Double) ?: 0.0,
            completedDate = dateOrNull("completedDate"),
            subtasks      = subtasksList,
            taskStatus    = runCatching { TaskStatus.valueOf(get("taskStatus") as? String ?: "NOT_STARTED") }
                .getOrDefault(TaskStatus.NOT_STARTED),
            scheduleWarning = parsedScheduleWarning,
            completedAssignments = completedAssignmentsSet
        )
    }
}