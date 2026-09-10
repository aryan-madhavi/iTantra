package com.astramesh.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.astramesh.storage.entity.AttachmentEntity
import com.astramesh.storage.entity.AuditLogEntity
import com.astramesh.storage.entity.ChatEntity
import com.astramesh.storage.entity.MessageEntity
import com.astramesh.storage.entity.PeerEntity
import com.astramesh.storage.entity.PendingQueueEntity
import com.astramesh.storage.entity.RouteEntity
import com.astramesh.storage.entity.SessionKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun observeAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(chat: ChatEntity)

    @Query("UPDATE chats SET unreadCount = 0 WHERE id = :chatId")
    suspend fun markAsRead(chatId: String)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)

    @Query("DELETE FROM chats")
    suspend fun clearAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers ORDER BY lastSeenTimestamp DESC")
    fun observeAllPeers(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE nodeId = :nodeId")
    suspend fun getPeerByNodeId(nodeId: Long): PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(peer: PeerEntity)

    @Query("UPDATE peers SET trustLevel = :trustLevel WHERE nodeId = :nodeId")
    suspend fun updateTrustLevel(nodeId: Long, trustLevel: String)
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes WHERE expireTimestamp > :now")
    fun observeActiveRoutes(now: Long = System.currentTimeMillis()): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE destination = :destination AND expireTimestamp > :now")
    suspend fun getActiveRoute(destination: Long, now: Long = System.currentTimeMillis()): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(route: RouteEntity)

    @Query("DELETE FROM routes WHERE expireTimestamp <= :now")
    suspend fun evictExpired(now: Long = System.currentTimeMillis()): Int
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE id = :id")
    fun observeAttachment(id: String): Flow<AttachmentEntity?>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getAttachment(id: String): AttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(attachment: AttachmentEntity)

    @Query("UPDATE attachments SET completedChunks = :completedChunks WHERE id = :id")
    suspend fun updateCompletedChunks(id: String, completedChunks: Int)
}

@Dao
interface PendingQueueDao {
    @Query("SELECT * FROM pending_queue WHERE nextRetryTimestamp <= :now ORDER BY priority ASC, nextRetryTimestamp ASC")
    suspend fun getPendingForRetry(now: Long = System.currentTimeMillis()): List<PendingQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: PendingQueueEntity)

    @Query("DELETE FROM pending_queue WHERE id = :id")
    suspend fun remove(id: String)

    @Query("DELETE FROM pending_queue")
    suspend fun clearAll()
}

@Dao
interface SessionKeyDao {
    @Query("SELECT * FROM session_keys WHERE peerNodeId = :peerNodeId")
    suspend fun getSession(peerNodeId: Long): SessionKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: SessionKeyEntity)

    @Query("DELETE FROM session_keys WHERE peerNodeId = :peerNodeId")
    suspend fun deleteSession(peerNodeId: Long)

    @Query("DELETE FROM session_keys")
    suspend fun clearAll()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 500")
    fun observeRecentLogs(): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insertLog(log: AuditLogEntity)

    @Query("DELETE FROM audit_logs WHERE timestamp < :beforeTimestamp")
    suspend fun pruneLogs(beforeTimestamp: Long): Int
}
