package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ToolNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.ListRunConfigurationsResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject

class ListRunConfigurationsTool : AbstractMcpTool() {

    override val requiresPsiSync: Boolean = false

    override val name = ToolNames.LIST_RUN_CONFIGURATIONS

    override val description = """
        List the run configurations available in the IDE for the current project.

        Returns each configuration's stable id, display name, type, folder, selected state, supported executors, and extracted execution details (working directory, main class, Gradle tasks, before-run tasks).

        Parameters: project_path (optional, only needed with multiple projects open).

        Example: {}
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .build()

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val result = edtAction {
            val runManager = RunManager.getInstance(project)
            val selectedConfigurationId = runManager.selectedConfiguration?.uniqueID
            val configurations = RunConfigurationSupport
                .getAllSettings(project)
                .map { settings ->
                    RunConfigurationSupport.toRunConfigurationInfo(project, settings, selectedConfigurationId)
                }

            ListRunConfigurationsResult(
                runConfigurations = configurations,
                totalCount = configurations.size
            )
        }

        return createJsonResult(result)
    }
}
