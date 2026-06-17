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
    // without a custom serialiser dependency.
    // -------------------------------------------------------------------------
    private fun Project.toMap(): Map<String, Any> = mapOf(
        "id"           to id,
        "name"         to name,
        "clientName"   to clientName,
        "deadlineDate" to deadlineDate.toString(),
        "hoursNeeded"  to hoursNeeded,
        "ratePerHour"  to ratePerHour
    )

    private fun Map<String, Any>.toProject() = Project(
        id           = (get("id") as? Long)?.toInt() ?: 0,
        name         = get("name") as? String ?: "",
        clientName   = get("clientName") as? String ?: "",
        deadlineDate = LocalDate.parse(get("deadlineDate") as? String ?: LocalDate.now().toString()),
        hoursNeeded  = (get("hoursNeeded") as? Double) ?: 0.0,
        ratePerHour  = (get("ratePerHour") as? Double) ?: 0.0
    )
}