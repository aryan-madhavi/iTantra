package com.astramesh.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.astramesh.storage.dao.AttachmentDao
import com.astramesh.storage.dao.AuditLogDao
import com.astramesh.storage.dao.ChatDao
import com.astramesh.storage.dao.MessageDao
import com.astramesh.storage.dao.PeerDao
import com.astramesh.storage.dao.PendingQueueDao
import com.astramesh.storage.dao.RouteDao
import com.astramesh.storage.dao.SessionKeyDao
import com.astramesh.storage.entity.AttachmentEntity
import com.astramesh.storage.entity.AuditLogEntity
import com.astramesh.storage.entity.ChatEntity
import com.astramesh.storage.entity.MessageEntity
import com.astramesh.storage.entity.PeerEntity
import com.astramesh.storage.entity.PendingQueueEntity
import com.astramesh.storage.entity.RouteEntity
import com.astramesh.storage.entity.SessionKeyEntity

class AstraConverters {
    @TypeConverter
    fun fromByteArray(bytes: ByteArray?): String? {
        return bytes?.let { java.util.Base64.getEncoder().encodeToString(it) }
    }

    @TypeConverter
    fun toByteArray(data: String?): ByteArray? {
        return data?.let { java.util.Base64.getDecoder().decode(it) }
    }
}

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        PeerEntity::class,
        RouteEntity::class,
        AttachmentEntity::class,
        PendingQueueEntity::class,
        SessionKeyEntity::class,
        AuditLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(AstraConverters::class)
abstract class AstraDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun peerDao(): PeerDao
    abstract fun routeDao(): RouteDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun pendingQueueDao(): PendingQueueDao
    abstract fun sessionKeyDao(): SessionKeyDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val DATABASE_NAME = "astramesh.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN originalText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN originalLanguage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN translatedText TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN translatedLanguage TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN wasTranslated INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
