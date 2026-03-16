package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ParamNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ToolNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.StopRunExecutionResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class StopRunExecutionTool : AbstractMcpTool() {
    override val requiresPsiSync: Boolean = false

    override val name = ToolNames.STOP_RUN_EXECUTION

    override val description = """
        Request that a tracked run execution be stopped.

        This is intended for long-running apps started via `ide_run_configuration` after the client decides it no longer needs the process.

        Parameters: executionId (required), waitUntilStopped (optional, default: false), timeout (optional, default: 20000 when waitUntilStopped=true), project_path (optional).

        Example: {"executionId": "123e4567-e89b-12d3-a456-426614174000", "waitUntilStopped": true, "timeout": 10000}
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .stringProperty(ParamNames.EXECUTION_ID, "Tracked execution id returned by ide_run_configuration.", required = true)
        .booleanProperty(ParamNames.WAIT_UNTIL_STOPPED, "Whether to wait for the run to stop before returning. Default: false.")
        .intProperty(ParamNames.TIMEOUT, "Maximum time to wait for the run to stop in milliseconds when waitUntilStopped=true. Default: 20000.")
        .build()

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val executionId = arguments[ParamNames.EXECUTION_ID]?.jsonPrimitive?.contentOrNull?.trim()
        if (executionId.isNullOrEmpty()) {
            return createErrorResult("Missing required parameter: executionId")
        }

        val waitUntilStopped = arguments[ParamNames.WAIT_UNTIL_STOPPED]?.let { element ->
            element.jsonPrimitive.booleanOrNull
                ?: element.jsonPrimitive.contentOrNull?.toBooleanStrictOrNull()
                ?: return createErrorResult("Parameter '${ParamNames.WAIT_UNTIL_STOPPED}' must be a boolean.")
        } ?: false
        val timeoutMs = arguments[ParamNames.TIMEOUT]?.let { element ->
            element.jsonPrimitive.contentOrNull?.toIntOrNull()
                ?: return createErrorResult("Parameter '${ParamNames.TIMEOUT}' must be a positive integer.")
        } ?: RunConfigurationSupport.DEFAULT_TIMEOUT_MS
        if (timeoutMs <= 0) {
            return createErrorResult("Parameter '${ParamNames.TIMEOUT}' must be a positive integer.")
        }

        val stopResult = RunConfigurationExecutionService.getInstance(project)
            .requestStop(executionId, waitUntilStopped, timeoutMs)
            ?: return createErrorResult("Run execution '$executionId' was not found.")

        return createJsonResult(
            StopRunExecutionResult(
                executionId = executionId,
                stopRequested = stopResult.stopRequested,
                wasRunning = stopResult.wasRunning,
                completed = stopResult.completed,
                success = stopResult.success,
                exitCode = stopResult.exitCode,
                terminationReason = stopResult.terminationReason?.wireValue,
                waitOutcome = stopResult.waitOutcome,
                message = stopResult.message
            )
        )
    }
}
