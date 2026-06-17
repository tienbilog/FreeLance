package com.grp8.freelance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class SchedulerViewModel(application: Application) : AndroidViewModel(application) {

    // -------------------------------------------------------------------------
    // Repositories — one local (DataStore), one cloud (Firestore).
    // Only one is active at a time, switched by onUserChanged().
    // -------------------------------------------------------------------------
    private val localRepo  = ProjectRepository(application)
    private var cloudRepo: CloudRepository? = null

    // Active collection job — cancelled when the source switches.
    private var collectJob: Job? = null

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _dailyCap = MutableStateFlow(8.0)
    val dailyCap: StateFlow<Double> = _dailyCap.asStateFlow()

    private val _result = MutableStateFlow<ScheduleResult?>(null)
    val result: StateFlow<ScheduleResult?> = _result.asStateFlow()

    private var idCtr = 1

    // Start in guest mode on creation.
    init { collectFrom(local = true) }

    // -------------------------------------------------------------------------
    // Called from MainActivity whenever auth state changes.
    //
    // • null user   → guest mode  → read/write DataStore
    // • signed-in   → cloud mode  → read/write Firestore for that uid
    //
    // On first sign-in: any guest projects are migrated to the cloud and the
    // local store is cleared so we don't double-count them next time.
    // -------------------------------------------------------------------------
    fun onUserChanged(user: FirebaseUser?) {
        if (user == null || user.isAnonymous) {
            cloudRepo = null
            collectFrom(local = true)
        } else {
            val repo = CloudRepository(user.uid)
            cloudRepo = repo
            viewModelScope.launch {
                migrateGuestDataIfNeeded(repo)
                collectFrom(local = false)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Project mutations — delegate to whichever repo is active.
    // -------------------------------------------------------------------------
    fun addProject(name: String, clientName: String, deadline: LocalDate,
                   hours: Double, rate: Double) {
        _projects.value += Project(idCtr++, name, clientName, deadline, hours, rate)
        persist()
    }

    fun removeProject(id: Int) {
        _projects.value = _projects.value.filter { it.id != id }
        persist()
    }

    fun updateProject(id: Int, name: String, clientName: String,
                      deadline: LocalDate, hours: Double, rate: Double) {
        _projects.value = _projects.value.map { p ->
            if (p.id == id) p.copy(
                name         = name,
                clientName   = clientName,
                deadlineDate = deadline,
                hoursNeeded  = hours,
                ratePerHour  = rate
            ) else p
        }
        persist()
    }

    fun setDailyCap(hours: Double) {
        _dailyCap.value = hours.coerceIn(1.0, 16.0)
    }

    fun runScheduler() {
        _result.value = Scheduler.schedule(_projects.value, _dailyCap.value)
    }

    fun clearResult() { _result.value = null }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Switch the active Flow source (local DataStore or Firestore). */
    private fun collectFrom(local: Boolean) {
        collectJob?.cancel()
        val flow = if (local) localRepo.projectsFlow else cloudRepo!!.projectsFlow
        collectJob = viewModelScope.launch {
            flow.collect { saved ->
                _projects.value = saved
                idCtr = (saved.maxOfOrNull { it.id } ?: 0) + 1
            }
        }
    }

    /** Write current in-memory projects to whichever repo is active. */
    private fun persist() {
        viewModelScope.launch {
            cloudRepo?.save(_projects.value) ?: localRepo.save(_projects.value)
        }
    }

    /**
     * If the user signs in for the first time and has guest data, push it to
     * Firestore and wipe the local store so it isn't replayed on next guest session.
     */
    private suspend fun migrateGuestDataIfNeeded(repo: CloudRepository) {
        val guestProjects = localRepo.load()
        if (guestProjects.isNotEmpty()) {
            repo.save(guestProjects)
            localRepo.save(emptyList())
        }
    }
}