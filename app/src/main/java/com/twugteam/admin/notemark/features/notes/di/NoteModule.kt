package com.twugteam.admin.notemark.features.notes.di

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.twugteam.admin.notemark.core.data.syncing.SyncRepositoryImpl
import com.twugteam.admin.notemark.core.data.syncing.dataSource.SyncDataSource
import com.twugteam.admin.notemark.core.data.syncing.dataSource.SyncDataSourceImpl
import com.twugteam.admin.notemark.core.database.notes.NoteDao
import com.twugteam.admin.notemark.core.database.notes.NoteEntity
import com.twugteam.admin.notemark.core.domain.SyncRepository
import com.twugteam.admin.notemark.features.notes.data.dataSource.OfflineFirstDataSource
import com.twugteam.admin.notemark.features.notes.data.dataSource.SyncIntervalDataStoreImpl
import com.twugteam.admin.notemark.features.notes.data.dataSource.WorkManagerSyncStatusObserver
import com.twugteam.admin.notemark.features.notes.data.dataSource.localNoteDataSource.LocalNoteDataSource
import com.twugteam.admin.notemark.features.notes.data.dataSource.localNoteDataSource.RoomLocalNoteDataSource
import com.twugteam.admin.notemark.features.notes.data.dataSource.localSyncDataSource.LocalSyncDataSource
import com.twugteam.admin.notemark.features.notes.data.dataSource.localSyncDataSource.RoomLocalSyncDataSource
import com.twugteam.admin.notemark.features.notes.data.dataSource.preferencesDataStore.syncInterval.SyncIntervalDataStoreDataSource
import com.twugteam.admin.notemark.features.notes.data.dataSource.preferencesDataStore.syncInterval.SyncIntervalDataStoreDataSourceImpl
import com.twugteam.admin.notemark.features.notes.data.dataSource.remoteDataSource.KtorRemoteNoteDataSource
import com.twugteam.admin.notemark.features.notes.data.dataSource.remoteDataSource.RemoteNoteDataSource
import com.twugteam.admin.notemark.features.notes.data.paging.NoteRemoteMediator
import com.twugteam.admin.notemark.features.notes.domain.NoteRepository
import com.twugteam.admin.notemark.features.notes.domain.SyncIntervalDataStore
import com.twugteam.admin.notemark.features.notes.domain.SyncStatusObserver
import com.twugteam.admin.notemark.features.notes.presentation.noteDetail.NoteDetailViewModel
import com.twugteam.admin.notemark.features.notes.presentation.noteList.NoteListViewModel
import com.twugteam.admin.notemark.features.notes.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

@OptIn(ExperimentalPagingApi::class)
val noteModule = module {

    viewModelOf(::NoteListViewModel)
    viewModelOf(::NoteDetailViewModel)
    viewModelOf(::SettingsViewModel)

    singleOf(::OfflineFirstDataSource).bind<NoteRepository>()
    singleOf(::KtorRemoteNoteDataSource).bind<RemoteNoteDataSource>()
    singleOf(::RoomLocalNoteDataSource).bind<LocalNoteDataSource>()
    singleOf(::RoomLocalSyncDataSource).bind<LocalSyncDataSource>()
    singleOf(::SyncDataSourceImpl).bind<SyncDataSource>()
    singleOf(::SyncRepositoryImpl).bind<SyncRepository>()
    singleOf(::WorkManagerSyncStatusObserver).bind<SyncStatusObserver>()

    single<SyncIntervalDataStoreDataSource> {
        SyncIntervalDataStoreDataSourceImpl(
            dataStore = get(named("syncDataStore"))
        )
    }

    singleOf(::SyncIntervalDataStoreImpl).bind<SyncIntervalDataStore>()

    single {
        NoteRemoteMediator(
            database = get(),
            remoteNoteDataSource = get(),
            localNoteDataSource = get(),
            sessionStorage = get(),
            localSyncDataSource = get()
        )
    }

    single<Pager<Int, NoteEntity>> {
        Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 4,
                initialLoadSize = 20,
                enablePlaceholders = false
            ),
            remoteMediator = NoteRemoteMediator(
                database = get(),
                remoteNoteDataSource = get(),
                localNoteDataSource = get(),
                sessionStorage = get(),
                localSyncDataSource = get()
            ),
            pagingSourceFactory = {
                get<NoteDao>().getPagingNotes()
            }
        )
    }
}