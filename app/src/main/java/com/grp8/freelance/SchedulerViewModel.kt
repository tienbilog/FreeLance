package com.grp8.freelance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class SchedulerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _dailyCap = MutableStateFlow(8.0)
    val dailyCap: StateFlow<Double> = _dailyCap.asStateFlow()

    private val _result = MutableStateFlow<ScheduleResult?>(null)
    val result: StateFlow<ScheduleResult?> = _result.asStateFlow()

    private var idCtr = 1

    init {
        viewModelScope.launch {
            repository.projectsFlow.collect { saved ->
                _projects.value = saved
                idCtr = (saved.maxOfOrNull { it.id } ?: 0) + 1
            }
        }
    }

    fun addProject(name: String, clientName: String, deadline: LocalDate, hours: Double, rate: Int) {
        _projects.value = _projects.value + Project(idCtr++, name, clientName, deadline, hours, rate)
        viewModelScope.launch { repository.save(_projects.value) }
    }

    fun removeProject(id: Int) {
        _projects.value = _projects.value.filter { it.id != id }
        viewModelScope.launch { repository.save(_projects.value) }
    }

    fun setDailyCap(hours: Double) {
        _dailyCap.value = hours.coerceIn(1.0, 16.0)
    }

    fun runScheduler() {
        _result.value = Scheduler.schedule(_projects.value, _dailyCap.value)
    }

    fun clearResult() {
        _result.value = null
    }
}