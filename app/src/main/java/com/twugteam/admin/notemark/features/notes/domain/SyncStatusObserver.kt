package com.twugteam.admin.notemark.features.notes.domain

import androidx.work.WorkInfo
import kotlinx.coroutines.flow.Flow

interface SyncStatusObserver {
    fun getWorkInfoFlow(workName: String): Flow<List<WorkInfo>>
}