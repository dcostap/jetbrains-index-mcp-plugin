package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ParamNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ToolNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.RunConfigurationExecutionResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class RunConfigurationTool : AbstractMcpTool() {
    private data class LaunchRequest(
        val settingsId: String,
        val settingsName: String,
        val executorId: String,
        val timeoutMs: Int,
        val waitForMode: RunConfigurationSupport.WaitForMode,
        val maxLinesCount: Int,
        val truncateMode: RunConfigurationSupport.TruncateMode,
        val environment: ExecutionEnvironment
    )

    private sealed interface PreparationResult {
        data class Ready(val request: LaunchRequest) : PreparationResult
        data class Invalid(val error: String) : PreparationResult
    }

    override val requiresPsiSync: Boolean = false

    override val name = ToolNames.RUN_CONFIGURATION

    override val description = """
        Run a run configuration from the IDE by stable id or exact name and wait for a specific milestone up to a timeout.

        Prefer passing `id` from `ide_list_run_configurations`; names can be ambiguous. `executorId` defaults to the standard Run executor. `waitFor` defaults to `completed`. `timeout` defaults to 20000 ms. `maxLinesCount` defaults to 200 lines, and `truncateMode` defaults to `start`, which keeps the most recent output. Successful launches return an `executionId` that can be used with `ide_get_run_execution`, `ide_read_run_output`, and `ide_stop_run_execution`. The tool validates the configuration headlessly before launch and rejects configurations that require an "Edit before run" dialog.

        Parameters: id (optional), name (optional if id is provided), executorId (optional, default: Run), waitFor (optional: started|first_output|completed, default: completed), timeout (optional, default: 20000), maxLinesCount (optional, default: 200), truncateMode (optional: start|middle|end|none, default: start), project_path (optional).

        Example: {"id": "Application:Demo"} or {"name": "Demo", "executorId": "Debug", "waitFor": "first_output", "timeout": 60000}
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .stringProperty(ParamNames.ID, "Stable run configuration id returned by ide_list_run_configurations.")
        .stringProperty(ParamNames.NAME, "Exact run configuration name. Use when id is not available.")
        .stringProperty(ParamNames.EXECUTOR_ID, "Executor id to use, such as Run or Debug. Defaults to Run.")
        .enumProperty(
            ParamNames.WAIT_FOR,
            "How long to wait before returning: 'started', 'first_output', or 'completed'. Default: completed.",
            RunConfigurationSupport.WaitForMode.entries.map { it.wireValue }
        )
        .intProperty(ParamNames.TIMEOUT, "Maximum time to wait for completion in milliseconds. Default: 20000.")
        .intProperty(ParamNames.MAX_LINES_COUNT, "Maximum number of output lines to return when truncation is enabled. Default: 200.")
        .enumProperty(
            ParamNames.TRUNCATE_MODE,
            "How to truncate output when it exceeds maxLinesCount. 'start' keeps the newest lines, 'middle' keeps both ends, 'end' keeps the earliest lines, and 'none' disables truncation. Default: start.",
            RunConfigurationSupport.TruncateMode.entries.map { it.wireValue }
        )
        .build()

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val configurationId = arguments[ParamNames.ID]?.jsonPrimitive?.contentOrNull
        val configurationName = arguments[ParamNames.NAME]?.jsonPrimitive?.contentOrNull
        val executorId = arguments[ParamNames.EXECUTOR_ID]?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DefaultRunExecutor.EXECUTOR_ID
        val waitForValue = arguments[ParamNames.WAIT_FOR]?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.lowercase()
        val waitForMode = if (waitForValue == null) {
            RunConfigurationSupport.WaitForMode.COMPLETED
        } else {
            RunConfigurationSupport.WaitForMode.fromWireValue(waitForValue)
                ?: return createErrorResult(
                    "Unknown waitFor '$waitForValue'. Supported values: ${
                        RunConfigurationSupport.WaitForMode.entries.joinToString(", ") { it.wireValue }
                    }"
                )
        }
        val timeoutMs = arguments[ParamNames.TIMEOUT]?.let { element ->
            element.jsonPrimitive.contentOrNull?.toIntOrNull()
                ?: return createErrorResult("Parameter '${ParamNames.TIMEOUT}' must be a positive integer.")
        } ?: RunConfigurationSupport.DEFAULT_TIMEOUT_MS
        if (timeoutMs <= 0) {
            return createErrorResult("Parameter '${ParamNames.TIMEOUT}' must be a positive integer.")
        }

        val maxLinesCount = arguments[ParamNames.MAX_LINES_COUNT]?.let { element ->
            element.jsonPrimitive.contentOrNull?.toIntOrNull()
                ?: return createErrorResult("Parameter '${ParamNames.MAX_LINES_COUNT}' must be a positive integer.")
        } ?: RunConfigurationSupport.DEFAULT_MAX_LINES_COUNT
        if (maxLinesCount <= 0) {
            return createErrorResult("Parameter '${ParamNames.MAX_LINES_COUNT}' must be a positive integer.")
        }

        val truncateModeValue = arguments[ParamNames.TRUNCATE_MODE]?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.lowercase()
        val truncateMode = if (truncateModeValue == null) {
            RunConfigurationSupport.TruncateMode.START
        } else {
            RunConfigurationSupport.TruncateMode.fromWireValue(truncateModeValue)
                ?: return createErrorResult(
                    "Unknown truncateMode '$truncateModeValue'. Supported values: ${
                        RunConfigurationSupport.TruncateMode.entries.joinToString(", ") { it.wireValue }
                    }"
                )
        }

        return when (
            val preparation = prepareLaunch(
                project = project,
                configurationId = configurationId,
                configurationName = configurationName,
                executorId = executorId,
                timeoutMs = timeoutMs,
                waitForMode = waitForMode,
                maxLinesCount = maxLinesCount,
                truncateMode = truncateMode
            )
        ) {
            is PreparationResult.Invalid -> createErrorResult(preparation.error)
            is PreparationResult.Ready -> executeAndAwait(project, preparation.request)
        }
    }

    private suspend fun prepareLaunch(
        project: Project,
        configurationId: String?,
        configurationName: String?,
        executorId: String,
        timeoutMs: Int,
        waitForMode: RunConfigurationSupport.WaitForMode,
        maxLinesCount: Int,
        truncateMode: RunConfigurationSupport.TruncateMode
    ): PreparationResult {
        return edtAction {
            val resolution = RunConfigurationSupport.resolve(project, configurationId, configurationName)
            if (resolution.error != null) {
                return@edtAction PreparationResult.Invalid(resolution.error)
            }

            val settings = resolution.settings!!
            val availableExecutors = RunConfigurationSupport.getAvailableExecutors(project, settings)

            val executor = ExecutorRegistry.getInstance().getExecutorById(executorId)
                ?: return@edtAction PreparationResult.Invalid(
                    "Unknown executorId '$executorId'. Available executor ids for '${settings.name}': " +
                        RunConfigurationSupport.formatExecutorIds(availableExecutors)
                )

            if (!executor.isApplicable(project)) {
                return@edtAction PreparationResult.Invalid(
                    "Executor '$executorId' is not applicable for this project."
                )
            }

            if (availableExecutors.none { it.id == executorId }) {
                return@edtAction PreparationResult.Invalid(
                    "Run configuration '${settings.name}' does not support executorId '$executorId'. " +
                        "Available executor ids: ${RunConfigurationSupport.formatExecutorIds(availableExecutors)}"
                )
            }

            if (settings.isEditBeforeRun) {
                return@edtAction PreparationResult.Invalid(
                    "Run configuration '${settings.name}' has 'Edit before run' enabled. Disable it before invoking this tool."
                )
            }

            try {
                settings.checkSettings(executor)
            } catch (e: RuntimeConfigurationException) {
                return@edtAction PreparationResult.Invalid(
                    "Run configuration '${settings.name}' is invalid for executor '$executorId': ${e.localizedMessage ?: "Unknown configuration error"}"
                )
            }

            val environment = try {
                ExecutionEnvironmentBuilder.create(executor, settings)
                    .contentToReuse(null)
                    .dataContext(null)
                    .activeTarget()
                    .executionId(System.nanoTime())
                    .build()
            } catch (e: ExecutionException) {
                return@edtAction PreparationResult.Invalid(
                    "Failed to prepare run configuration '${settings.name}' for executor '$executorId': ${e.localizedMessage ?: "Unknown execution error"}"
                )
            }

            RunManager.getInstance(project).selectedConfiguration = settings

            PreparationResult.Ready(
                LaunchRequest(
                    settingsId = settings.uniqueID,
                    settingsName = settings.name,
                    executorId = executor.id,
                    timeoutMs = timeoutMs,
                    waitForMode = waitForMode,
                    maxLinesCount = maxLinesCount,
                    truncateMode = truncateMode,
                    environment = environment
                )
            )
        }
    }

    private suspend fun executeAndAwait(project: Project, launchRequest: LaunchRequest): ToolCallResult {
        val executionService = RunConfigurationExecutionService.getInstance(project)
        val executionId = executionService.registerExecution(
            id = launchRequest.settingsId,
            name = launchRequest.settingsName,
            executorId = launchRequest.executorId,
            environmentExecutionId = launchRequest.environment.executionId
        )

        try {
            edtAction {
                ProgramRunnerUtil.executeConfiguration(launchRequest.environment, false, false)
            }
        } catch (e: Exception) {
            val errorMessage =
                "Failed to start run configuration '${launchRequest.settingsName}': ${e.localizedMessage ?: "Unknown execution error"}"
            executionService.markLaunchFailure(executionId, errorMessage)
            return createErrorResult(errorMessage)
        }

        val waitOutcome = withContext(Dispatchers.IO) {
            executionService.await(executionId, launchRequest.waitForMode, launchRequest.timeoutMs)
        } ?: return createErrorResult("Run execution '$executionId' is no longer available.")

        val snapshot = executionService.getSnapshot(executionId)
            ?: return createErrorResult("Run execution '$executionId' is no longer available.")

        val truncatedOutput = RunConfigurationSupport.truncateOutput(
            output = snapshot.output,
            maxLinesCount = launchRequest.maxLinesCount,
            mode = launchRequest.truncateMode
        )

        if (snapshot.status == RunConfigurationExecutionService.ExecutionState.FAILED) {
            return createErrorResult(snapshot.message ?: "Run configuration '${launchRequest.settingsName}' failed to start.")
        }

        val completed = snapshot.completed
        val timedOut = waitOutcome == RunConfigurationSupport.WaitOutcome.TIMEOUT
        val exitCode = snapshot.exitCode
        val success = snapshot.success
        val message = when {
            waitOutcome == RunConfigurationSupport.WaitOutcome.STARTED -> {
                "Run configuration '${launchRequest.settingsName}' started with executor '${launchRequest.executorId}'."
            }

            waitOutcome == RunConfigurationSupport.WaitOutcome.FIRST_OUTPUT -> {
                "Run configuration '${launchRequest.settingsName}' produced output with executor '${launchRequest.executorId}'."
            }

            timedOut -> {
                "Started run configuration '${launchRequest.settingsName}' with executor '${launchRequest.executorId}' and waited ${launchRequest.timeoutMs} ms for '${launchRequest.waitForMode.wireValue}'; it is still running. Use '${
                    ToolNames.GET_RUN_EXECUTION
                }', '${ToolNames.READ_RUN_OUTPUT}', or '${ToolNames.STOP_RUN_EXECUTION}' with executionId '$executionId' to follow up."
            }

            completed && exitCode == null -> {
                snapshot.message ?: "Run configuration '${launchRequest.settingsName}' finished, but no exit code was reported."
            }

            success == true -> {
                "Run configuration '${launchRequest.settingsName}' finished successfully with executor '${launchRequest.executorId}'."
            }

            else -> {
                "Run configuration '${launchRequest.settingsName}' finished with exit code ${exitCode ?: "unknown"} using executor '${launchRequest.executorId}'."
            }
        }

        return createJsonResult(
            RunConfigurationExecutionResult(
                executionId = executionId,
                id = launchRequest.settingsId,
                name = launchRequest.settingsName,
                executorId = launchRequest.executorId,
                waitFor = launchRequest.waitForMode.wireValue,
                waitOutcome = waitOutcome.wireValue,
                started = true,
                completed = completed,
                timedOut = timedOut,
                success = success,
                exitCode = exitCode,
                terminationReason = snapshot.terminationReason?.wireValue,
                output = truncatedOutput.text,
                outputLength = snapshot.outputOffset,
                lastChunkLength = snapshot.lastChunkLength,
                truncated = truncatedOutput.truncated,
                timeoutMs = launchRequest.timeoutMs,
                message = message
            )
        )
    }
}
