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
import java.time.YearMonth

class SchedulerViewModel(application: Application) : AndroidViewModel(application) {

    // -------------------------------------------------------------------------
    // Repositories — one local (DataStore), one cloud (Firestore).
    // Only one is active at a time, switched by onUserChanged().
    // -------------------------------------------------------------------------
    private val localRepo  = ProjectRepository(application)
    private var cloudRepo: CloudRepository? = null
    private var collectJob: Job? = null

    /** The single source of truth — every project, regardless of phase/status. */
    private val _allProjects = MutableStateFlow<List<Project>>(emptyList())
    val allProjects: StateFlow<List<Project>> = _allProjects.asStateFlow()

    private val _dailyCap = MutableStateFlow(8.0)
    val dailyCap: StateFlow<Double> = _dailyCap.asStateFlow()

    /** Phase 1→2 preview — populated by runOptimizer(), cleared by acceptSchedule()/discard. */
    private val _suggested = MutableStateFlow<ScheduleResult?>(null)
    val suggested: StateFlow<ScheduleResult?> = _suggested.asStateFlow()

    /** Phase 3 pacing feedback — populated whenever hours are logged. */
    private val _paceStatus = MutableStateFlow<PaceStatus>(PaceStatus.OnTrack)
    val paceStatus: StateFlow<PaceStatus> = _paceStatus.asStateFlow()

    private var idCtr = 1

    init { collectFrom(local = true) }

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

    // =========================================================================
    // DERIVED VIEWS — each tab reads a filtered slice of allProjects
    // =========================================================================
    val potentialProjects: List<Project> get() = _allProjects.value.filter { it.status == ProjectStatus.POTENTIAL }
    val scheduledProjects: List<Project> get() = _allProjects.value.filter { it.status == ProjectStatus.SCHEDULED }
    val doneProjects: List<Project>      get() = _allProjects.value.filter { it.status == ProjectStatus.DONE }

    // =========================================================================
    // PHASE 1 — OPTIMIZER: add/edit/remove potential projects
    // =========================================================================
    fun addPotentialProject(
        name: String, clientName: String, deadline: LocalDate, hours: Double,
        rateType: RateType, ratePerHour: Double, fixedAmount: Double
    ) {
        val project = Project(
            id = idCtr++, name = name, clientName = clientName, deadlineDate = deadline,
            hoursNeeded = hours, rateType = rateType, ratePerHour = ratePerHour,
            fixedAmount = fixedAmount, status = ProjectStatus.POTENTIAL
        )
        _allProjects.value += project
        persist()
    }

    fun updatePotentialProject(
        id: Int, name: String, clientName: String, deadline: LocalDate, hours: Double,
        rateType: RateType, ratePerHour: Double, fixedAmount: Double
    ) {
        _allProjects.value = _allProjects.value.map { p ->
            if (p.id == id) p.copy(
                name = name, clientName = clientName, deadlineDate = deadline,
                hoursNeeded = hours, rateType = rateType, ratePerHour = ratePerHour,
                fixedAmount = fixedAmount
            ) else p
        }
        persist()
        // If a suggestion is showing, it's now stale — clear it so the user re-runs.
        _suggested.value = null
    }

    fun removePotentialProject(id: Int) {
        _allProjects.value = _allProjects.value.filter { it.id != id }
        persist()
        _suggested.value = null
    }

    fun setDailyCap(hours: Double) {
        _dailyCap.value = hours.coerceIn(1.0, 16.0)
    }

    // =========================================================================
    // PHASE 1 → 2 — run the optimizer on potential projects (preview only)
    // =========================================================================
    fun runOptimizer() {
        _suggested.value = Scheduler.schedule(potentialProjects, _dailyCap.value)
    }

    fun discardSuggestion() { _suggested.value = null }

    /**
     * PHASE 2 → 3 — commit the suggested schedule.
     * Accepted projects flip to SCHEDULED with their assigned date locked in;
     * everything else (including never-fit projects) stays POTENTIAL so the
     * user can adjust and re-run later.
     */
    fun acceptSchedule() {
        val result = _suggested.value ?: return
        val acceptedIds = result.accepted.associate { it.project.id to it.assignedDate }

        _allProjects.value = _allProjects.value.map { p ->
            val assignedDate = acceptedIds[p.id]
            if (assignedDate != null) {
                p.copy(status = ProjectStatus.SCHEDULED, assignedDate = assignedDate)
            } else p
        }
        _suggested.value = null
        persist()
    }

    // =========================================================================
    // PHASE 3 — TO-DO LIST: logging hours, pace tracking, rescheduling
    // =========================================================================

    /** User updates how many hours they've actually worked on a scheduled project. */
    fun logHours(projectId: Int, hoursWorked: Double) {
        _allProjects.value = _allProjects.value.map { p ->
            if (p.id == projectId) p.copy(hoursLogged = hoursWorked) else p
        }
        persist()
        recomputePace()
    }

    /**
     * Marks a scheduled project as finished. Locks in the actual hours worked
     * (defaulting to the original estimate if never logged) and flips it to DONE,
     * which immediately surfaces it on the Income tab.
     */
    fun markDone(projectId: Int) {
        _allProjects.value = _allProjects.value.map { p ->
            if (p.id == projectId) p.copy(
                status = ProjectStatus.DONE,
                completedDate = LocalDate.now(),
                hoursLogged = if (p.hoursLogged > 0.0) p.hoursLogged else p.hoursNeeded
            ) else p
        }
        persist()
        recomputePace()
    }

    /**
     * Re-optimizes the remaining (not-yet-done) scheduled projects.
     *
     * Used in two situations, both routed through this one function:
     *  • "Ahead" — user finished early, wants to pull future projects forward.
     *    Pass no override; the freed-up capacity from completed/lighter-than-
     *    estimated work is already reflected via hoursRemaining.
     *  • "Behind" — user grants a one-time capacity boost to a specific future
     *    day to help catch up. Pass that single day/hours pair as the override.
     */
    fun rescheduleRemaining(capacityOverride: Pair<LocalDate, Double>? = null) {
        val remaining = scheduledProjects
        if (remaining.isEmpty()) return

        val overrides = capacityOverride?.let { (date, hours) -> mapOf(date to hours) } ?: emptyMap()
        val result = Scheduler.reschedule(remaining, _dailyCap.value, overrides)

        val newDates = result.accepted.associate { it.project.id to it.assignedDate }
        _allProjects.value = _allProjects.value.map { p ->
            val newDate = newDates[p.id]
            if (newDate != null) p.copy(assignedDate = newDate) else p
        }
        persist()
        recomputePace()
    }

    /**
     * Recomputes whether the user is ahead of, behind, or on track with their
     * schedule, based on logged hours vs. original estimates across all
     * still-active (SCHEDULED, not yet DONE) projects.
     *
     * Ahead:  a project was finished using fewer hours than estimated.
     * Behind: cumulative hours logged across unfinished projects exceed what
     *         was estimated for the time already elapsed toward their deadlines.
     */
    private fun recomputePace() {
        val active = scheduledProjects
        if (active.isEmpty()) {
            _paceStatus.value = PaceStatus.OnTrack
            return
        }

        // "Ahead" signal: any project finished today logged fewer hours than estimated.
        val finishedToday = doneProjects.filter { it.completedDate == LocalDate.now() }
        val hoursSaved = finishedToday.sumOf { (it.hoursNeeded - it.hoursLogged).coerceAtLeast(0.0) }

        // "Behind" signal: hours logged so far beyond estimate on still-open projects.
        val hoursOver = active.sumOf { (it.hoursLogged - it.hoursNeeded).coerceAtLeast(0.0) }

        _paceStatus.value = when {
            hoursSaved > 0.0 -> PaceStatus.Ahead(hoursSaved)
            hoursOver  > 0.0 -> PaceStatus.Behind(hoursOver)
            else              -> PaceStatus.OnTrack
        }
    }

    fun acknowledgePace() { _paceStatus.value = PaceStatus.OnTrack }

    // =========================================================================
    // PHASE 4 — MONTHLY INCOME
    // =========================================================================
    fun incomeForMonth(month: YearMonth): MonthlyIncomeSummary {
        val entries = doneProjects
            .filter { it.completedDate != null && YearMonth.from(it.completedDate) == month }
            .map { IncomeEntry(it, it.completedDate!!, it.totalIncome) }
            .sortedBy { it.completedDate }
        return MonthlyIncomeSummary(month, entries)
    }

    fun availableIncomeMonths(): List<YearMonth> =
        doneProjects.mapNotNull { it.completedDate?.let { d -> YearMonth.from(d) } }
            .distinct().sortedDescending()

    // =========================================================================
    // Private helpers
    // =========================================================================
    private fun collectFrom(local: Boolean) {
        collectJob?.cancel()
        val flow = if (local) localRepo.projectsFlow else cloudRepo!!.projectsFlow
        collectJob = viewModelScope.launch {
            flow.collect { saved ->
                _allProjects.value = saved
                idCtr = (saved.maxOfOrNull { it.id } ?: 0) + 1
            }
        }
    }

    private fun persist() {
        viewModelScope.launch {
            cloudRepo?.save(_allProjects.value) ?: localRepo.save(_allProjects.value)
        }
    }

    private suspend fun migrateGuestDataIfNeeded(repo: CloudRepository) {
        val guestProjects = localRepo.load()
        if (guestProjects.isNotEmpty()) {
            repo.save(guestProjects)
            localRepo.save(emptyList())
        }
    }
}