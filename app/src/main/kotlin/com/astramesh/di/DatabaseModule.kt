package com.astramesh.di

import android.content.Context
import androidx.room.Room
import com.astramesh.storage.AstraDatabase
import com.astramesh.storage.dao.AttachmentDao
import com.astramesh.storage.dao.AuditLogDao
import com.astramesh.storage.dao.ChatDao
import com.astramesh.storage.dao.MessageDao
import com.astramesh.storage.dao.PeerDao
import com.astramesh.storage.dao.PendingQueueDao
import com.astramesh.storage.dao.RouteDao
import com.astramesh.storage.dao.SessionKeyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAstraDatabase(@ApplicationContext context: Context): AstraDatabase {
        return Room.databaseBuilder(
            context,
            AstraDatabase::class.java,
            AstraDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideChatDao(database: AstraDatabase): ChatDao = database.chatDao()

    @Provides
    fun provideMessageDao(database: AstraDatabase): MessageDao = database.messageDao()

    @Provides
    fun providePeerDao(database: AstraDatabase): PeerDao = database.peerDao()

    @Provides
    fun provideRouteDao(database: AstraDatabase): RouteDao = database.routeDao()

    @Provides
    fun provideAttachmentDao(database: AstraDatabase): AttachmentDao = database.attachmentDao()

    @Provides
    fun providePendingQueueDao(database: AstraDatabase): PendingQueueDao = database.pendingQueueDao()

    @Provides
    fun provideSessionKeyDao(database: AstraDatabase): SessionKeyDao = database.sessionKeyDao()

    @Provides
    fun provideAuditLogDao(database: AstraDatabase): AuditLogDao = database.auditLogDao()
}
