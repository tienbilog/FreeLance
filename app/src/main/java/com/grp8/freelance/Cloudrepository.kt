package com.grp8.freelance

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

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
        "assignedDate"  to (assignedDate?.toString() ?: ""),
        "hoursLogged"   to hoursLogged,
        "completedDate" to (completedDate?.toString() ?: "")
    )

    private fun Map<String, Any>.toProject(): Project {
        fun dateOrNull(key: String): LocalDate? {
            val raw = get(key) as? String
            return if (raw.isNullOrBlank()) null else LocalDate.parse(raw)
        }

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
            assignedDate  = dateOrNull("assignedDate"),
            hoursLogged   = (get("hoursLogged") as? Double) ?: 0.0,
            completedDate = dateOrNull("completedDate")
        )
    }
}