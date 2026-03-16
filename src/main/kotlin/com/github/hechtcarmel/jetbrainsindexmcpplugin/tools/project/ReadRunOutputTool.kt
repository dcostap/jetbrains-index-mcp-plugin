package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ParamNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ToolNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.RunExecutionOutputResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class ReadRunOutputTool : AbstractMcpTool() {
    override val requiresPsiSync: Boolean = false

    override val name = ToolNames.READ_RUN_OUTPUT

    override val description = """
        Read output from a tracked run execution starting at a character offset.

        Use the `executionId` from `ide_run_configuration` and the latest `nextOffset` from prior reads to poll incrementally without duplicating output.

        Parameters: executionId (required), since (optional, default: 0), project_path (optional).

        Example: {"executionId": "123e4567-e89b-12d3-a456-426614174000", "since": 120}
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .stringProperty(ParamNames.EXECUTION_ID, "Tracked execution id returned by ide_run_configuration.", required = true)
        .intProperty(ParamNames.SINCE, "Character offset to read from. Default: 0.")
        .build()

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val executionId = arguments[ParamNames.EXECUTION_ID]?.jsonPrimitive?.contentOrNull?.trim()
        if (executionId.isNullOrEmpty()) {
            return createErrorResult("Missing required parameter: executionId")
        }

        val since = arguments[ParamNames.SINCE]?.let { element ->
            element.jsonPrimitive.contentOrNull?.toIntOrNull()
                ?: return createErrorResult("Parameter '${ParamNames.SINCE}' must be a non-negative integer.")
        } ?: 0
        if (since < 0) {
            return createErrorResult("Parameter '${ParamNames.SINCE}' must be a non-negative integer.")
        }

        val executionService = RunConfigurationExecutionService.getInstance(project)
        val snapshot = executionService.getSnapshot(executionId)
            ?: return createErrorResult("Run execution '$executionId' was not found.")
        val chunk = executionService.readOutput(executionId, since)
            ?: return createErrorResult("Run execution '$executionId' was not found.")

        return createJsonResult(
            RunExecutionOutputResult(
                executionId = executionId,
                status = snapshot.status.wireValue,
                completed = snapshot.completed,
                success = snapshot.success,
                exitCode = snapshot.exitCode,
                terminationReason = snapshot.terminationReason?.wireValue,
                since = since,
                nextOffset = chunk.nextOffset,
                outputLength = chunk.outputLength,
                lastChunkLength = chunk.lastChunkLength,
                output = chunk.output
            )
        )
    }
}
