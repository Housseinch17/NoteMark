package com.twugteam.admin.notemark.features.notes.data.dataSource

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.twugteam.admin.notemark.features.notes.domain.SyncStatusObserver
import kotlinx.coroutines.flow.Flow

class WorkManagerSyncStatusObserver(
    private val workManager: WorkManager
): SyncStatusObserver {
    override fun getWorkInfoFlow(workName: String): Flow<List<WorkInfo>> {
        //access the workInfo state
            return workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName = workName)
    }
}