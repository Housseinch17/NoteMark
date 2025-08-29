package com.twugteam.admin.notemark.features.notes.presentation.settings

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.twugteam.admin.notemark.R
import com.twugteam.admin.notemark.core.domain.SyncRepository
import com.twugteam.admin.notemark.core.domain.network.ConnectivityObserver
import com.twugteam.admin.notemark.core.domain.util.Result
import com.twugteam.admin.notemark.core.presentation.ui.UiText
import com.twugteam.admin.notemark.core.presentation.ui.asUiText
import com.twugteam.admin.notemark.features.notes.constant.Constants.MANUAL_SYNC_WORK_NAME
import com.twugteam.admin.notemark.features.notes.data.model.SyncInterval
import com.twugteam.admin.notemark.features.notes.domain.NoteRepository
import com.twugteam.admin.notemark.features.notes.domain.SyncIntervalDataStore
import com.twugteam.admin.notemark.features.notes.domain.SyncStatusObserver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SettingsViewModel(
    private val noteRepository: NoteRepository,
    private val syncRepository: SyncRepository,
    private val syncIntervalDataStore: SyncIntervalDataStore,
    private val connectivityObserver: ConnectivityObserver,
    private val syncStatusObserver: SyncStatusObserver,
) : ViewModel() {
    private val _state: MutableStateFlow<SettingsUiState> = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    private val _events = Channel<SettingsEvents>()
    val events = _events.receiveAsFlow()

    private val manualWorkName = MANUAL_SYNC_WORK_NAME

    init {
        //each one should run internally on an independent scope
        //read interval and timeUnit from dataStore
        getInterval()
        //launch on independent scope
        getLastSyncTimestamp()
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun onActions(settingsActions: SettingsActions) {
        when (settingsActions) {
            SettingsActions.OnBack -> onBack()
            SettingsActions.OnLogoutClick -> onLogoutClick()
            SettingsActions.OnExpand -> onExpand()
            is SettingsActions.SyncWithInterval -> onSyncIntervalSelect(syncInterval = settingsActions.syncInterval)
            SettingsActions.ManualSync -> syncData()
            is SettingsActions.OnLogOutConfirm -> onLogoutConfirm(withSyncing = settingsActions.withSyncing)
            SettingsActions.OnDismissRequest -> onDismissRequest()
        }
    }

    private fun onDismissRequest() {
        _state.update { newState ->
            newState.copy(
                isLoading = false,
                showDialog = false,
            )
        }
    }

    //confirming logout either with syncing or without
    private fun onLogoutConfirm(withSyncing: Boolean) {
        viewModelScope.launch {
            _state.update { newState ->
                newState.copy(
                    isLoading = true
                )
            }

            if (withSyncing) {
                //start syncing
                syncRepository.manualSync()

                val workInfo = syncStatusObserver.getWorkInfoFlow(workName = manualWorkName)
                workInfo.collectLatest { workInfo ->
                    if (workInfo.firstOrNull() != null) {
                        Timber.tag("SyncingWorker").d("workInfo: ${workInfo.first().state}")
                        when (workInfo.first().state) {
                            WorkInfo.State.SUCCEEDED -> {
                                //sync succeed, logout now
                                logOut()
                            }

                            WorkInfo.State.FAILED -> {
                                _state.update { newState ->
                                    newState.copy(
                                        isLoading = false,
                                        showDialog = false
                                    )
                                }
                                showSnackBar(
                                    snackBarText = UiText.StringResource(R.string.logout_sync_failed),
                                    delay = 3.seconds
                                )
                            }

                            WorkInfo.State.CANCELLED -> {
                                _state.update { newState ->
                                    newState.copy(
                                        isLoading = false,
                                        showDialog = false
                                    )
                                }
                                showSnackBar(
                                    snackBarText = UiText.StringResource(R.string.logout_sync_failed),
                                    delay = 3.seconds
                                )
                            }

                            WorkInfo.State.ENQUEUED -> {
                                Timber.tag("SyncingWorker").d("sync: Enqueued")
                            }

                            WorkInfo.State.RUNNING -> {
                                Timber.tag("SyncingWorker").d("sync: RUNNING")
                            }

                            WorkInfo.State.BLOCKED -> {
                                Timber.tag("SyncingWorker").d("sync: BLOCKED")
                            }
                        }
                    }
                }
                return@launch
            }

            //logOut  directly if without sync
            logOut()
        }
    }

    private suspend fun logOut() {
        val logOut = noteRepository.logOut()

        //after logOut finish hide dialog and hide loader before checking if
        //success or error
        _state.update { newState ->
            newState.copy(
                showDialog = false,
                isLoading = false
            )
        }

        //small delay to avoid showing dialog on the navigated screen after logout
        delay(100.milliseconds)

        when (logOut) {
            is Result.Error -> {
                showSnackBar(snackBarText = logOut.error.asUiText())
            }

            is Result.Success -> {

                _events.send(SettingsEvents.OnLogout)
            }
        }
    }

    private fun getInterval() {
        viewModelScope.launch {
            syncIntervalDataStore.getInterval().collectLatest { (interval, text, timeUnit) ->
                _state.update { newState ->
                    newState.copy(
                        selectedSyncingInterval = newState.selectedSyncingInterval.copy(
                            interval = interval,
                            text = text,
                            timeUnit = timeUnit
                        )
                    )
                }
            }
        }
    }

    private fun getLastSyncTimestamp() {
        viewModelScope.launch {
            syncIntervalDataStore.getLastSyncTimestamp().collectLatest { lastSyncTimestamp ->
                _state.update { newState ->
                    newState.copy(
                        lastSyncTimestamp = lastSyncTimestamp
                    )
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun syncData() {
        viewModelScope.launch {
            syncRepository.manualSync()
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun syncWithInterval(interval: Long?, timeUnit: TimeUnit?) {
        viewModelScope.launch {
            syncRepository.syncWithInterval(interval = interval, timeUnit = timeUnit)
        }
    }


    private fun onExpand() {
        val isExpanded = _state.value.isSyncExpanded
        _state.update { newState ->
            newState.copy(
                isSyncExpanded = !isExpanded
            )
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun onSyncIntervalSelect(syncInterval: SyncInterval) {
        viewModelScope.launch {
            val interval = syncInterval.interval
            val text = syncInterval.text
            val timeUnit = syncInterval.timeUnit

            //save into interval data store
            val saveIntoDataStore = syncIntervalDataStore.saveInterval(
                interval = interval,
                text = text,
                timeUnit = timeUnit
            )

            when (saveIntoDataStore) {
                is Result.Error -> {
                    Timber.tag("MyTag")
                        .e("onSyncIntervalSelect: error : ${saveIntoDataStore.error}")
                }

                is Result.Success -> {
                    _state.update { newState ->
                        newState.copy(
                            selectedSyncingInterval = newState.selectedSyncingInterval.copy(
                                text = syncInterval.text
                            )
                        )
                    }
                    //sync after saving
                    syncWithInterval(
                        interval = syncInterval.interval,
                        timeUnit = syncInterval.timeUnit
                    )
                }
            }

        }
    }

    private fun onBack() {
        viewModelScope.launch {
            _events.send(SettingsEvents.OnBack)
        }
    }

    private fun onLogoutClick() {
        val isCurrentlyConnected = connectivityObserver.isCurrentlyConnected()
        Timber.tag("MyTag").d("isCurrentlyConnected: $isCurrentlyConnected")
        if (isCurrentlyConnected) {
            _state.update { newState ->
                newState.copy(
                    showDialog = true,
                )
            }
        } else {
            showSnackBar(
                snackBarText = UiText.StringResource(R.string.no_internet)
            )
        }
    }

    private fun showSnackBar(snackBarText: UiText, delay: Duration = 2.seconds) {
        viewModelScope.launch {
            _state.update { newState ->
                newState.copy(
                    showSnackBar = true,
                    snackBarText = snackBarText
                )
            }
            //show snackBar for 2 seconds
            delay(delay)

            //reset state
            _state.update { newState ->
                newState.copy(
                    showSnackBar = false,
                    snackBarText = UiText.DynamicString("")
                )
            }
        }
    }

}