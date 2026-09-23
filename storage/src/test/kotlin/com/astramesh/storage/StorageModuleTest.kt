package com.astramesh.storage

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astramesh.storage.entity.ChatEntity
import com.astramesh.storage.entity.MessageEntity
import com.astramesh.storage.entity.RouteEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorageModuleTest {

    private lateinit var db: AstraDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AstraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `chat dao inserts and retrieves chats`() = runTest {
        val chat = ChatEntity(
            id = "chat_1",
            title = "Mesh Direct Chat",
            type = "DIRECT",
            participantIdsJson = "[]",
            lastMessageSnippet = "Hello",
            unreadCount = 2,
            updatedAt = 1000L
        )

        db.chatDao().insertOrUpdate(chat)
        val fetched = db.chatDao().getChatById("chat_1")
        assertThat(fetched).isEqualTo(chat)

        db.chatDao().markAsRead("chat_1")
        val updated = db.chatDao().getChatById("chat_1")
        assertThat(updated?.unreadCount).isEqualTo(0)
    }

    @Test
    fun `message dao inserts and observes messages in chronological order`() = runTest {
        val msg1 = MessageEntity(
            id = "m1",
            chatId = "c1",
            senderId = 10L,
            recipientId = 20L,
            timestamp = 100L,
            content = "First",
            originalText = "First",
            originalLanguage = "ENGLISH",
            translatedText = null,
            translatedLanguage = null,
            wasTranslated = false,
            contentType = "TEXT",
            status = "DELIVERED",
            priority = 2,
            hopCount = 0,
            ttl = 7,
            attachmentId = null
        )
        val msg2 = MessageEntity(
            id = "m2",
            chatId = "c1",
            senderId = 20L,
            recipientId = 10L,
            timestamp = 200L,
            content = "Second",
            originalText = "Second",
            originalLanguage = "ENGLISH",
            translatedText = null,
            translatedLanguage = null,
            wasTranslated = false,
            contentType = "TEXT",
            status = "READ",
            priority = 2,
            hopCount = 1,
            ttl = 6,
            attachmentId = null
        )

        db.messageDao().insertMessage(msg2)
        db.messageDao().insertMessage(msg1)

        val list = db.messageDao().observeMessages("c1").first()
        assertThat(list).hasSize(2)
        assertThat(list[0].id).isEqualTo("m1")
        assertThat(list[1].id).isEqualTo("m2")
    }

    @Test
    fun `route dao stores and evicts expired routes`() = runTest {
        val now = 1000L
        val activeRoute = RouteEntity(
            destination = 100L,
            nextHop = 50L,
            cost = 1.0f,
            hopCount = 1,
            sequenceNumber = 1L,
            expireTimestamp = now + 5000L
        )
        val expiredRoute = RouteEntity(
            destination = 200L,
            nextHop = 60L,
            cost = 2.0f,
            hopCount = 2,
            sequenceNumber = 1L,
            expireTimestamp = now - 100L
        )

        db.routeDao().insertOrUpdate(activeRoute)
        db.routeDao().insertOrUpdate(expiredRoute)

        val fetchedActive = db.routeDao().getActiveRoute(100L, now)
        assertThat(fetchedActive).isEqualTo(activeRoute)

        val fetchedExpired = db.routeDao().getActiveRoute(200L, now)
        assertThat(fetchedExpired).isNull()

        val evictedCount = db.routeDao().evictExpired(now)
        assertThat(evictedCount).isEqualTo(1)
    }
}
