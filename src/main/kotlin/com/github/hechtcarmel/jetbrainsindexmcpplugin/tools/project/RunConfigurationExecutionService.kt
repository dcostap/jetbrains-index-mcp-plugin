package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class RunConfigurationExecutionService(private val project: Project) {
    companion object {
        private const val MAX_TRACKED_EXECUTIONS = 100
        private const val WAIT_SLICE_MS = 100L

        fun getInstance(project: Project): RunConfigurationExecutionService = project.service()

        internal fun resolveWaitOutcome(
            snapshot: ExecutionSnapshot,
            waitForMode: RunConfigurationSupport.WaitForMode
        ): RunConfigurationSupport.WaitOutcome? {
            return when (waitForMode) {
                RunConfigurationSupport.WaitForMode.STARTED -> when {
                    snapshot.hasStarted -> RunConfigurationSupport.WaitOutcome.STARTED
                    snapshot.completed -> RunConfigurationSupport.WaitOutcome.COMPLETED
                    else -> null
                }

                RunConfigurationSupport.WaitForMode.FIRST_OUTPUT -> when {
                    snapshot.hasOutput -> RunConfigurationSupport.WaitOutcome.FIRST_OUTPUT
                    snapshot.completed -> RunConfigurationSupport.WaitOutcome.COMPLETED
                    else -> null
                }

                RunConfigurationSupport.WaitForMode.COMPLETED -> when {
                    snapshot.completed -> RunConfigurationSupport.WaitOutcome.COMPLETED
                    else -> null
                }
            }
        }
    }

    internal enum class ExecutionState(val wireValue: String) {
        STARTING("starting"),
        RUNNING("running"),
        COMPLETED("completed"),
        FAILED("failed"),
        STOP_REQUESTED("stop_requested"),
        STOPPED("stopped")
    }

    internal data class ExecutionSnapshot(
        val executionId: String,
        val id: String,
        val name: String,
        val executorId: String,
        val status: ExecutionState,
        val stopRequested: Boolean,
        val success: Boolean?,
        val exitCode: Int?,
        val terminationReason: RunConfigurationSupport.TerminationReason?,
        val startedAtMs: Long,
        val finishedAtMs: Long?,
        val message: String?,
        val output: String,
        val outputOffset: Int,
        val lastChunkLength: Int,
        val hasStarted: Boolean,
        val hasOutput: Boolean
    ) {
        val completed: Boolean
            get() = status == ExecutionState.COMPLETED || status == ExecutionState.FAILED || status == ExecutionState.STOPPED

        val running: Boolean
            get() = status == ExecutionState.STARTING || status == ExecutionState.RUNNING || status == ExecutionState.STOP_REQUESTED
    }

    internal data class OutputChunk(
        val output: String,
        val nextOffset: Int,
        val outputLength: Int,
        val lastChunkLength: Int
    )

    internal data class StopRequestResult(
        val stopRequested: Boolean,
        val wasRunning: Boolean,
        val completed: Boolean,
        val success: Boolean?,
        val exitCode: Int?,
        val terminationReason: RunConfigurationSupport.TerminationReason?,
        val waitOutcome: String,
        val message: String
    )

    private inner class TrackedExecution(
        val executionId: String,
        val id: String,
        val name: String,
        val executorId: String,
        private val environmentExecutionId: Long
    ) {
        private val outputLock = Any()
        private val output = StringBuilder()
        private val completionLatch = CountDownLatch(1)
        private val startedLatch = CountDownLatch(1)
        private val firstOutputLatch = CountDownLatch(1)
        private val completionRecorded = AtomicBoolean(false)
        private val startedRecorded = AtomicBoolean(false)
        private val firstOutputRecorded = AtomicBoolean(false)
        private val connection = project.messageBus.connect()

        @Volatile
        private var state: ExecutionState = ExecutionState.STARTING

        @Volatile
        private var stopRequested = false

        @Volatile
        private var success: Boolean? = null

        @Volatile
        private var exitCode: Int? = null

        @Volatile
        private var message: String? = null

        @Volatile
        private var finishedAtMs: Long? = null

        @Volatile
        private var handler: ProcessHandler? = null

        @Volatile
        private var terminationReason: RunConfigurationSupport.TerminationReason? = null

        @Volatile
        private var lastChunkLength = 0

        private val startedAtMs = System.currentTimeMillis()

        init {
            connection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
                override fun processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
                    if (env.executionId != environmentExecutionId) return
                    attachHandler(handler)
                }

                override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
                    if (env.executionId != environmentExecutionId) return
                    attachHandler(handler)
                }

                override fun processNotStarted(executorId: String, env: ExecutionEnvironment, cause: Throwable?) {
                    if (env.executionId != environmentExecutionId) return
                    recordFailure(
                        failureMessage = "Run configuration '$name' failed to start: ${cause?.message ?: "Unknown execution error"}",
                        failureReason = RunConfigurationSupport.TerminationReason.LAUNCH_FAILED
                    )
                }

                override fun processTerminated(
                    executorId: String,
                    env: ExecutionEnvironment,
                    handler: ProcessHandler,
                    exitCode: Int
                ) {
                    if (env.executionId != environmentExecutionId) return
                    recordTermination(exitCode)
                }
            })
        }

        fun await(waitForMode: RunConfigurationSupport.WaitForMode, timeoutMs: Int): RunConfigurationSupport.WaitOutcome {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.toLong())

            while (true) {
                val snapshot = snapshot()
                resolveWaitOutcome(snapshot, waitForMode)?.let { outcome ->
                    return outcome
                }

                val remainingNanos = deadline - System.nanoTime()
                if (remainingNanos <= 0) {
                    resolveWaitOutcome(snapshot(), waitForMode)?.let { outcome ->
                        return outcome
                    }
                    return RunConfigurationSupport.WaitOutcome.TIMEOUT
                }

                val waitMillis = minOf(TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1), WAIT_SLICE_MS)
                when (waitForMode) {
                    RunConfigurationSupport.WaitForMode.STARTED -> startedLatch.await(waitMillis, TimeUnit.MILLISECONDS)
                    RunConfigurationSupport.WaitForMode.FIRST_OUTPUT -> {
                        firstOutputLatch.await(waitMillis, TimeUnit.MILLISECONDS)
                        completionLatch.await(0, TimeUnit.MILLISECONDS)
                    }

                    RunConfigurationSupport.WaitForMode.COMPLETED -> completionLatch.await(waitMillis, TimeUnit.MILLISECONDS)
                }
            }
        }

        fun markLaunchFailure(message: String) {
            recordFailure(message, RunConfigurationSupport.TerminationReason.LAUNCH_FAILED)
        }

        fun snapshot(): ExecutionSnapshot {
            val outputText = synchronized(outputLock) { output.toString() }
            return ExecutionSnapshot(
                executionId = executionId,
                id = id,
                name = name,
                executorId = executorId,
                status = state,
                stopRequested = stopRequested,
                success = success,
                exitCode = exitCode,
                terminationReason = terminationReason,
                startedAtMs = startedAtMs,
                finishedAtMs = finishedAtMs,
                message = message,
                output = outputText,
                outputOffset = outputText.length,
                lastChunkLength = lastChunkLength,
                hasStarted = startedRecorded.get(),
                hasOutput = outputText.isNotEmpty() || firstOutputRecorded.get()
            )
        }

        fun readOutput(since: Int): OutputChunk {
            val safeSince = since.coerceAtLeast(0)
            return synchronized(outputLock) {
                val currentOutput = output.toString()
                val startOffset = safeSince.coerceAtMost(currentOutput.length)
                val chunk = currentOutput.substring(startOffset)
                OutputChunk(
                    output = chunk,
                    nextOffset = currentOutput.length,
                    outputLength = currentOutput.length,
                    lastChunkLength = chunk.length
                )
            }
        }

        fun requestStop(waitUntilStopped: Boolean, timeoutMs: Int): StopRequestResult {
            val currentSnapshot = snapshot()
            if (currentSnapshot.completed) {
                return StopRequestResult(
                    stopRequested = false,
                    wasRunning = false,
                    completed = true,
                    success = currentSnapshot.success,
                    exitCode = currentSnapshot.exitCode,
                    terminationReason = currentSnapshot.terminationReason,
                    waitOutcome = RunConfigurationSupport.WaitOutcome.COMPLETED.wireValue,
                    message = "Run execution '$executionId' has already completed."
                )
            }

            stopRequested = true
            if (state == ExecutionState.STARTING || state == ExecutionState.RUNNING) {
                state = ExecutionState.STOP_REQUESTED
            }

            handler?.let { processHandler ->
                if (!processHandler.isProcessTerminated && !processHandler.isProcessTerminating) {
                    processHandler.destroyProcess()
                }
            }

            val waitOutcome = if (waitUntilStopped) {
                await(RunConfigurationSupport.WaitForMode.COMPLETED, timeoutMs)
            } else {
                RunConfigurationSupport.WaitOutcome.STARTED
            }
            val snapshot = snapshot()

            return StopRequestResult(
                stopRequested = true,
                wasRunning = currentSnapshot.running,
                completed = snapshot.completed,
                success = snapshot.success,
                exitCode = snapshot.exitCode,
                terminationReason = snapshot.terminationReason,
                waitOutcome = if (waitUntilStopped) waitOutcome.wireValue else "requested",
                message = when {
                    waitUntilStopped && waitOutcome == RunConfigurationSupport.WaitOutcome.TIMEOUT ->
                        "Stop requested for run execution '$executionId', but it did not finish within ${timeoutMs} ms."

                    snapshot.completed ->
                        "Run execution '$executionId' has stopped."

                    handler == null ->
                        "Stop requested for run execution '$executionId'. The process has not attached yet."

                    else ->
                        "Stop requested for run execution '$executionId'."
                }
            )
        }

        private fun attachHandler(processHandler: ProcessHandler) {
            if (handler != null) {
                return
            }

            handler = processHandler
            markStarted()
            state = if (stopRequested) ExecutionState.STOP_REQUESTED else ExecutionState.RUNNING
            processHandler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    synchronized(outputLock) {
                        output.append(event.text)
                        lastChunkLength = event.text.length
                    }
                    if (event.text.isNotEmpty() && firstOutputRecorded.compareAndSet(false, true)) {
                        firstOutputLatch.countDown()
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    recordTermination(event.exitCode)
                }
            })

            if (stopRequested && !processHandler.isProcessTerminated && !processHandler.isProcessTerminating) {
                processHandler.destroyProcess()
            }
        }

        private fun markStarted() {
            if (startedRecorded.compareAndSet(false, true)) {
                startedLatch.countDown()
            }
        }

        private fun recordFailure(
            failureMessage: String,
            failureReason: RunConfigurationSupport.TerminationReason
        ) {
            message = failureMessage
            success = false
            terminationReason = if (stopRequested) RunConfigurationSupport.TerminationReason.STOPPED_BY_USER else failureReason
            state = if (stopRequested) ExecutionState.STOPPED else ExecutionState.FAILED
            finishedAtMs = System.currentTimeMillis()
            firstOutputLatch.countDown()
            completeOnce()
        }

        private fun recordTermination(processExitCode: Int) {
            exitCode = processExitCode
            if (stopRequested) {
                success = false
                terminationReason = RunConfigurationSupport.TerminationReason.STOPPED_BY_USER
                state = ExecutionState.STOPPED
            } else if (processExitCode == 0) {
                success = true
                terminationReason = RunConfigurationSupport.TerminationReason.COMPLETED
                state = ExecutionState.COMPLETED
            } else {
                success = false
                terminationReason = RunConfigurationSupport.TerminationReason.NON_ZERO_EXIT
                state = ExecutionState.FAILED
            }
            finishedAtMs = System.currentTimeMillis()
            firstOutputLatch.countDown()
            completeOnce()
        }

        private fun completeOnce() {
            markStarted()
            if (completionRecorded.compareAndSet(false, true)) {
                completionLatch.countDown()
                connection.disconnect()
            }
        }
    }

    private val executions = ConcurrentHashMap<String, TrackedExecution>()

    fun registerExecution(
        id: String,
        name: String,
        executorId: String,
        environmentExecutionId: Long
    ): String {
        pruneCompletedExecutionsIfNeeded()
        val executionId = UUID.randomUUID().toString()
        executions[executionId] = TrackedExecution(
            executionId = executionId,
            id = id,
            name = name,
            executorId = executorId,
            environmentExecutionId = environmentExecutionId
        )
        return executionId
    }

    internal fun await(
        executionId: String,
        waitForMode: RunConfigurationSupport.WaitForMode,
        timeoutMs: Int
    ): RunConfigurationSupport.WaitOutcome? {
        return executions[executionId]?.await(waitForMode, timeoutMs)
    }

    fun markLaunchFailure(executionId: String, message: String) {
        executions[executionId]?.markLaunchFailure(message)
    }

    internal fun getSnapshot(executionId: String): ExecutionSnapshot? {
        return executions[executionId]?.snapshot()
    }

    internal fun readOutput(executionId: String, since: Int): OutputChunk? {
        return executions[executionId]?.readOutput(since)
    }

    internal fun requestStop(
        executionId: String,
        waitUntilStopped: Boolean,
        timeoutMs: Int
    ): StopRequestResult? {
        return executions[executionId]?.requestStop(waitUntilStopped, timeoutMs)
    }

    private fun pruneCompletedExecutionsIfNeeded() {
        if (executions.size < MAX_TRACKED_EXECUTIONS) {
            return
        }

        val candidates = executions.values
            .map { it.snapshot() }
            .filter { it.completed }
            .sortedBy { it.finishedAtMs ?: it.startedAtMs }

        val overflowCount = executions.size - MAX_TRACKED_EXECUTIONS + 1
        candidates.take(overflowCount).forEach { executions.remove(it.executionId) }
    }
}
