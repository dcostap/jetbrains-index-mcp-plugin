package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project

import junit.framework.TestCase

class RunConfigurationSupportUnitTest : TestCase() {

    private class FakeExternalSystemSettings(
        private val taskNames: List<String>,
        private val externalProjectPath: String
    ) {
        fun getTaskNames(): List<String> = taskNames
        fun getExternalProjectPath(): String = externalProjectPath
    }

    private class FakeGradleConfiguration(
        private val settings: FakeExternalSystemSettings
    ) {
        fun getSettings(): FakeExternalSystemSettings = settings
    }

    fun testTruncateOutputStartKeepsNewestLines() {
        val output = (1..5).joinToString("\n") { "line-$it" }

        val result = RunConfigurationSupport.truncateOutput(output, 3, RunConfigurationSupport.TruncateMode.START)

        assertTrue(result.truncated)
        assertEquals("... [3 lines omitted] ...\nline-4\nline-5", result.text)
    }

    fun testTruncateOutputMiddleKeepsBothEnds() {
        val output = (1..6).joinToString("\n") { "line-$it" }

        val result = RunConfigurationSupport.truncateOutput(output, 5, RunConfigurationSupport.TruncateMode.MIDDLE)

        assertTrue(result.truncated)
        assertEquals("line-1\nline-2\n... [2 lines omitted] ...\nline-5\nline-6", result.text)
    }

    fun testTruncateOutputEndKeepsEarliestLines() {
        val output = (1..5).joinToString("\n") { "line-$it" }

        val result = RunConfigurationSupport.truncateOutput(output, 4, RunConfigurationSupport.TruncateMode.END)

        assertTrue(result.truncated)
        assertEquals("line-1\nline-2\nline-3\n... [2 lines omitted] ...", result.text)
    }

    fun testTruncateOutputNoneLeavesOutputUntouched() {
        val output = "line-1\nline-2\nline-3"

        val result = RunConfigurationSupport.truncateOutput(output, 1, RunConfigurationSupport.TruncateMode.NONE)

        assertFalse(result.truncated)
        assertEquals(output, result.text)
    }

    fun testExtractTaskNamesFromExternalSystemSettings() {
        val configuration = FakeGradleConfiguration(
            FakeExternalSystemSettings(
                taskNames = listOf("assemble", "test"),
                externalProjectPath = "C:\\project"
            )
        )

        assertEquals(listOf("assemble", "test"), RunConfigurationSupport.extractTaskNames(configuration))
    }

    fun testExtractWorkingDirectoryFromExternalSystemSettings() {
        val configuration = FakeGradleConfiguration(
            FakeExternalSystemSettings(
                taskNames = listOf("assemble"),
                externalProjectPath = "C:\\project\\composeApp"
            )
        )

        assertEquals("C:\\project\\composeApp", RunConfigurationSupport.extractWorkingDirectory(configuration))
    }

    fun testWaitForModeFromWireValue() {
        assertEquals(
            RunConfigurationSupport.WaitForMode.FIRST_OUTPUT,
            RunConfigurationSupport.WaitForMode.fromWireValue("first_output")
        )
        assertNull(RunConfigurationSupport.WaitForMode.fromWireValue("timeout"))
    }

    fun testResolveWaitOutcomeReturnsFirstOutputWhenOutputIsAvailable() {
        val snapshot = RunConfigurationExecutionService.ExecutionSnapshot(
            executionId = "exec-1",
            id = "Application:Demo",
            name = "Demo",
            executorId = "Run",
            status = RunConfigurationExecutionService.ExecutionState.RUNNING,
            stopRequested = false,
            success = null,
            exitCode = null,
            terminationReason = null,
            startedAtMs = 1L,
            finishedAtMs = null,
            message = null,
            output = "hello",
            outputOffset = 5,
            lastChunkLength = 5,
            hasStarted = true,
            hasOutput = true
        )

        assertEquals(
            RunConfigurationSupport.WaitOutcome.FIRST_OUTPUT,
            RunConfigurationExecutionService.resolveWaitOutcome(
                snapshot,
                RunConfigurationSupport.WaitForMode.FIRST_OUTPUT
            )
        )
    }

    fun testResolveWaitOutcomeReturnsCompletedWhenExecutionFinishesBeforeOutput() {
        val snapshot = RunConfigurationExecutionService.ExecutionSnapshot(
            executionId = "exec-2",
            id = "Application:Demo",
            name = "Demo",
            executorId = "Run",
            status = RunConfigurationExecutionService.ExecutionState.COMPLETED,
            stopRequested = false,
            success = true,
            exitCode = 0,
            terminationReason = RunConfigurationSupport.TerminationReason.COMPLETED,
            startedAtMs = 1L,
            finishedAtMs = 2L,
            message = null,
            output = "",
            outputOffset = 0,
            lastChunkLength = 0,
            hasStarted = true,
            hasOutput = false
        )

        assertEquals(
            RunConfigurationSupport.WaitOutcome.COMPLETED,
            RunConfigurationExecutionService.resolveWaitOutcome(
                snapshot,
                RunConfigurationSupport.WaitForMode.FIRST_OUTPUT
            )
        )
    }
}
