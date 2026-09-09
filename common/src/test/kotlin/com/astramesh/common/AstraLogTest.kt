package com.astramesh.common

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class AstraLogTest {

    @Before
    fun setup() {
        AstraLog.clearLogs()
        AstraLog.minLevel = AstraLogLevel.VERBOSE
        AstraLog.enableConsoleOutput = false
    }

    @Test
    fun `logs are buffered in ring buffer and filter by level`() {
        AstraLog.minLevel = AstraLogLevel.INFO

        AstraLog.d("TestTag", "Debug message should be ignored")
        AstraLog.i("TestTag", "Info message")
        AstraLog.w("TestTag", "Warning message")
        AstraLog.e("TestTag", "Error message")

        val logs = AstraLog.getRecentLogs()
        assertThat(logs).hasSize(3)
        assertThat(logs[0].message).isEqualTo("Info message")
        assertThat(logs[1].message).isEqualTo("Warning message")
        assertThat(logs[2].message).isEqualTo("Error message")
    }

    @Test
    fun `ring buffer truncates when exceeding maximum capacity`() {
        AstraLog.minLevel = AstraLogLevel.VERBOSE
        for (i in 1..600) {
            AstraLog.d("Tag", "Log #$i")
        }

        val logs = AstraLog.getRecentLogs()
        assertThat(logs.size).isEqualTo(500)
        assertThat(logs.last().message).isEqualTo("Log #600")
        assertThat(logs.first().message).isEqualTo("Log #101")
    }
}
