package com.screenusdeletus.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screenusdeletus.AppContainer
import com.screenusdeletus.data.repo.DashboardData
import com.screenusdeletus.data.repo.StoredRule
import com.screenusdeletus.security.ProtectionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Dashboard + analytics: recomputed from usage stats every 30 s while visible. */
class StatsViewModel(private val c: AppContainer) : ViewModel() {
    private val _data = MutableStateFlow<DashboardData?>(null)
    val data: StateFlow<DashboardData?> = _data.asStateFlow()

    val protection: StateFlow<ProtectionStatus?> = c.tamper.status

    init {
        viewModelScope.launch {
            runCatching { c.stats.rollOverCompletedDays() }
            while (isActive) {
                refresh()
                delay(30_000)
            }
        }
        viewModelScope.launch { runCatching { c.tamper.check() } }
    }

    suspend fun refresh() {
        runCatching { c.stats.dashboard() }.onSuccess { _data.value = it }
    }

    val unlockedUntil: Long get() = c.unlocks.unlockedUntil
}

class RulesViewModel(private val c: AppContainer) : ViewModel() {
    val rules: StateFlow<List<StoredRule>> = c.rules.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _usage = MutableStateFlow<Map<String, Long>>(emptyMap())
    val usage: StateFlow<Map<String, Long>> = _usage.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                val today = c.usage.today()
                _usage.value = rules.value.associate { it.rule.id to c.usage.usageForRule(it.rule, today) }
                delay(15_000)
            }
        }
    }

    fun cancelPending(id: String) = viewModelScope.launch { c.rules.cancelPending(id) }
}

class SecurityViewModel(private val c: AppContainer) : ViewModel() {
    val status: StateFlow<ProtectionStatus?> = c.tamper.status
    val events = c.tamper.observeEvents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            c.tamper.check(forceIntegrity = true)
            while (isActive) {
                delay(5_000)
                c.tamper.check()
            }
        }
    }

    fun requestPause() = viewModelScope.launch { c.tamper.requestDisarm(); c.tamper.check() }
    fun cancelPause() = viewModelScope.launch { c.tamper.cancelDisarm(); c.tamper.check() }
    fun pauseNow() = viewModelScope.launch { c.tamper.disarmNow(); c.tamper.check() }
}
