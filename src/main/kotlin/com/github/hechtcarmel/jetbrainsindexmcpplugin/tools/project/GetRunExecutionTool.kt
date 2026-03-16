package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ParamNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ToolNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.RunExecutionStatusResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class GetRunExecutionTool : AbstractMcpTool() {
    override val requiresPsiSync: Boolean = false

    override val name = ToolNames.GET_RUN_EXECUTION

    override val description = """
        Get the current status of a tracked run execution returned by `ide_run_configuration`.

        Use this after a timed-out run to see whether the process is still running, completed successfully, failed, or was stopped. Returns the current output offset so clients can poll `ide_read_run_output`.

        Parameters: executionId (required), project_path (optional).

        Example: {"executionId": "123e4567-e89b-12d3-a456-426614174000"}
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .stringProperty(ParamNames.EXECUTION_ID, "Tracked execution id returned by ide_run_configuration.", required = true)
        .build()

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val executionId = arguments[ParamNames.EXECUTION_ID]?.jsonPrimitive?.contentOrNull?.trim()
        if (executionId.isNullOrEmpty()) {
            return createErrorResult("Missing required parameter: executionId")
        }

        val snapshot = RunConfigurationExecutionService.getInstance(project).getSnapshot(executionId)
            ?: return createErrorResult("Run execution '$executionId' was not found.")

        return createJsonResult(
            RunExecutionStatusResult(
                executionId = snapshot.executionId,
                id = snapshot.id,
                name = snapshot.name,
                executorId = snapshot.executorId,
                status = snapshot.status.wireValue,
                running = snapshot.running,
                completed = snapshot.completed,
                stopRequested = snapshot.stopRequested,
                success = snapshot.success,
                exitCode = snapshot.exitCode,
                terminationReason = snapshot.terminationReason?.wireValue,
                outputOffset = snapshot.outputOffset,
                outputLength = snapshot.outputOffset,
                lastChunkLength = snapshot.lastChunkLength,
                startedAtMs = snapshot.startedAtMs,
                finishedAtMs = snapshot.finishedAtMs,
                message = snapshot.message
            )
        )
    }
}
