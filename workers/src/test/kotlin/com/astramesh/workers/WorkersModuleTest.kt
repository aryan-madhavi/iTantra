package com.astramesh.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WorkersModuleTest {

    @Test
    fun `workers return success result on execution`() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val mockParams = mockk<WorkerParameters>(relaxed = true)

        val queueWorker = PendingQueueSyncWorker(mockContext, mockParams)
        val keyWorker = KeyRotationWorker(mockContext, mockParams)
        val pruneWorker = DatabasePruneWorker(mockContext, mockParams)

        assertThat(queueWorker.doWork()).isEqualTo(ListenableWorker.Result.success())
        assertThat(keyWorker.doWork()).isEqualTo(ListenableWorker.Result.success())
        assertThat(pruneWorker.doWork()).isEqualTo(ListenableWorker.Result.success())
    }
}
