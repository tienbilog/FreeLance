package com.grp8.freelance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek

class SchedulerViewModel(application: Application) : AndroidViewModel(application) {

    // -------------------------------------------------------------------------
    // Repositories — one local (DataStore), one cloud (Firestore).
    // Only one is active at a time, switched by onUserChanged().
    // -------------------------------------------------------------------------
    private val localRepo  = ProjectRepository(application)
    private var cloudRepo: CloudRepository? = null
    private var collectJob: Job? = null
    private var scheduleCollectJob: Job? = null

    /** The single source of truth — every project, regardless of phase/status. */
    private val _allProjects = MutableStateFlow<List<Project>>(emptyList())
    val allProjects: StateFlow<List<Project>> = _allProjects.asStateFlow()

    private val _weeklySchedule = MutableStateFlow<Map<DayOfWeek, Double>>(emptyMap())
    val weeklySchedule: StateFlow<Map<DayOfWeek, Double>> = _weeklySchedule.asStateFlow()

    /** Phase 1→2 preview — populated by runOptimizer(), cleared by acceptSchedule()/discard. */
    private val _suggested = MutableStateFlow<ScheduleResult?>(null)
    val suggested: StateFlow<ScheduleResult?> = _suggested.asStateFlow()

    /** Phase 3 pacing feedback — populated whenever hours are logged. */
    private val _paceStatus = MutableStateFlow<PaceStatus>(PaceStatus.OnTrack)
    val paceStatus: StateFlow<PaceStatus> = _paceStatus.asStateFlow()

    private var idCtr = 1

    val hasCompletedOnboarding: StateFlow<Boolean> = localRepo.hasCompletedOnboardingFlow
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)

    init { collectFrom(local = true) }

    fun completeOnboarding() {
        viewModelScope.launch {
            localRepo.setOnboardingCompleted()
        }
    }

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
    /** The 7 dates (Mon–Sun) of the current calendar week, for the weekly schedule picker. */
    fun currentWeekDates(): List<LocalDate> {
        val today = LocalDate.now()
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0..6).map { monday.plusDays(it.toLong()) }
    }

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
        runOptimizer()
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
        runOptimizer()
    }

    fun removePotentialProject(id: Int) {
        _allProjects.value = _allProjects.value.filter { it.id != id }
        persist()
        runOptimizer()
    }

    fun deleteAllProjects() {
        _allProjects.value = emptyList()
        persist()
        runOptimizer()
    }

    /**
     * Sets which days this week the user is dedicating time to work, and how
     * many hours each. Days not included fall back to a small emergency
     * capacity ([UNSELECTED_DAY_FALLBACK_HOURS]) rather than zero.
     */
    fun setWeeklySchedule(dayHours: Map<DayOfWeek, Double>) {
        _weeklySchedule.value = dayHours.mapValues { (_, h) -> h.coerceIn(0.0, 16.0) }
    }

    fun saveWeeklySchedule(dayHours: Map<DayOfWeek, Double>) {
        setWeeklySchedule(dayHours)
        viewModelScope.launch {
            cloudRepo?.saveSchedule(_weeklySchedule.value) ?: localRepo.saveSchedule(_weeklySchedule.value)
        }
        runOptimizer()
    }

    // =========================================================================
    // PHASE 1 → 2 — run the optimizer on potential projects (preview only)
    // =========================================================================
    fun runOptimizer() {
        if (potentialProjects.isEmpty()) {
            _suggested.value = null
        } else {
            _suggested.value = Scheduler.schedule(potentialProjects, _weeklySchedule.value)
        }
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
        val acceptedAssignments = result.accepted.associate { it.project.id to it.assignments }

        _allProjects.value = _allProjects.value.map { p ->
            val newAssignments = acceptedAssignments[p.id]
            if (newAssignments != null) {
                val totalAccounted = newAssignments.values.sum() + p.hoursLogged
                val warning = if (totalAccounted < p.hoursNeeded - 0.01) {
                    "Project could not be fully scheduled before its deadline. (${totalAccounted.fmt()}h scheduled / ${p.hoursNeeded.fmt()}h total)"
                } else null
                p.copy(status = ProjectStatus.SCHEDULED, assignedDates = newAssignments, scheduleWarning = warning)
            } else p
        }
        _suggested.value = null
        persist()
    }

    // =========================================================================
    // PHASE 3 — TO-DO LIST: logging hours, pace tracking, rescheduling
    // =========================================================================

    /**
     * Marks a scheduled project as finished, given the ACTUAL hours it took.
     * This immediately determines whether the user finished ahead of or
     * behind their original estimate, and updates [paceStatus] accordingly
     * so the To-Do screen can offer the right follow-up action:
     *   • Ahead  → offer to re-run the optimizer and move remaining work up
     *   • Behind → suggest boosting a future day's capacity to catch up
     */
    fun completeAssignment(projectId: Int, date: LocalDate, actualHours: Double) {
        val project = _allProjects.value.find { it.id == projectId } ?: return

        _allProjects.value = _allProjects.value.map { p ->
            if (p.id == projectId) {
                val newCompletedAssignments = p.completedAssignments + date
                val newHoursLogged = p.hoursLogged + actualHours
                
                val allAssignmentsCompleted = p.assignedDates.keys.all { it in newCompletedAssignments }
                val isStrictlyCompleted = allAssignmentsCompleted && p.scheduleWarning == null && p.assignedDates.values.sum() >= p.hoursNeeded - 0.01
                
                val newTaskStatus = when {
                    newCompletedAssignments.isEmpty() -> TaskStatus.NOT_STARTED
                    allAssignmentsCompleted -> TaskStatus.COMPLETED
                    else -> TaskStatus.ONGOING
                }
                
                p.copy(
                    completedAssignments = newCompletedAssignments,
                    hoursLogged = newHoursLogged,
                    status = if (isStrictlyCompleted) ProjectStatus.DONE else ProjectStatus.SCHEDULED,
                    completedDate = if (isStrictlyCompleted) LocalDate.now() else null,
                    taskStatus = newTaskStatus
                )
            } else p
        }
        persist()

        val scheduledHoursForDate = project.assignedDates[date] ?: 0.0
        val delta = scheduledHoursForDate - actualHours
        _paceStatus.value = when {
            delta > 0.0  -> PaceStatus.Ahead(delta)
            delta < 0.0  -> PaceStatus.Behind(-delta)
            else         -> PaceStatus.OnTrack
        }
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
        val result = Scheduler.reschedule(remaining, _weeklySchedule.value, capacityOverrides = overrides)

        val newDates = result.accepted.associate { it.project.id to it.assignments }
        val today = LocalDate.now()
        
        _allProjects.value = _allProjects.value.map { p ->
            if (p.status != ProjectStatus.SCHEDULED) return@map p
            
            val pastAssignments = p.assignedDates.filterKeys { it.isBefore(today) }
            val newFutureAssignments = newDates[p.id] ?: emptyMap()
            val mergedAssignments = pastAssignments + newFutureAssignments
            
            val totalScheduled = mergedAssignments.values.sum()
            val totalAccounted = totalScheduled + p.hoursLogged
            
            val warning = if (totalAccounted < p.hoursNeeded - 0.01) {
                "Project could not be fully scheduled before its deadline. (${totalAccounted.fmt()}h scheduled / ${p.hoursNeeded.fmt()}h total)"
            } else null
            
            p.copy(assignedDates = mergedAssignments, scheduleWarning = warning)
        }
        persist()
        // The reschedule action itself resolves whatever ahead/behind state
        // triggered it, so clear the banner once applied.
        _paceStatus.value = PaceStatus.OnTrack
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

    fun deleteAllIncomeRecords() {
        _allProjects.value = _allProjects.value.filter { it.status != ProjectStatus.DONE }
        persist()
    }

    private fun Project.withRecalculatedStatus(): Project {
        val newStatus = when {
            assignedDates.isEmpty() -> TaskStatus.NOT_STARTED
            completedAssignments.isEmpty() -> TaskStatus.NOT_STARTED
            assignedDates.keys.all { it in completedAssignments } -> TaskStatus.COMPLETED
            else -> TaskStatus.ONGOING
        }
        return this.copy(taskStatus = newStatus)
    }

    fun addSubtask(projectId: Int, title: String) {
        _allProjects.value = _allProjects.value.map { p ->
            if (p.id == projectId) {
                p.copy(subtasks = p.subtasks + Subtask(title = title)).withRecalculatedStatus()
            } else p
        }
        persist()
    }

    fun toggleSubtask(projectId: Int, subtaskId: String) {
        _allProjects.value = _allProjects.value.map { p ->
            if (p.id == projectId) {
                p.copy(subtasks = p.subtasks.map { s ->
                    if (s.id == subtaskId) s.copy(isCompleted = !s.isCompleted) else s
                }).withRecalculatedStatus()
            } else p
        }
        persist()
    }

    fun removeSubtask(projectId: Int, subtaskId: String) {
        _allProjects.value = _allProjects.value.map { p ->
            if (p.id == projectId) {
                p.copy(subtasks = p.subtasks.filter { it.id != subtaskId }).withRecalculatedStatus()
            } else p
        }
        persist()
    }

    fun updateTaskStatus(projectId: Int, status: TaskStatus) {
        _allProjects.value = _allProjects.value.map { p ->
            if (p.id == projectId) p.copy(taskStatus = status) else p
        }
        persist()
    }

    // =========================================================================
    // Private helpers
    // =========================================================================
    private fun collectFrom(local: Boolean) {
        collectJob?.cancel()
        scheduleCollectJob?.cancel()
        
        val projectFlow = if (local) localRepo.projectsFlow else cloudRepo!!.projectsFlow
        collectJob = viewModelScope.launch {
            projectFlow.collect { saved ->
                _allProjects.value = saved
                idCtr = (saved.maxOfOrNull { it.id } ?: 0) + 1
            }
        }
        
        val schedFlow = if (local) localRepo.scheduleFlow else cloudRepo!!.scheduleFlow
        scheduleCollectJob = viewModelScope.launch {
            schedFlow.collect { savedSched ->
                _weeklySchedule.value = savedSched
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
        val guestSchedule = localRepo.loadSchedule()
        if (guestSchedule.isNotEmpty()) {
            repo.saveSchedule(guestSchedule)
            localRepo.saveSchedule(emptyMap())
        }
    }
}