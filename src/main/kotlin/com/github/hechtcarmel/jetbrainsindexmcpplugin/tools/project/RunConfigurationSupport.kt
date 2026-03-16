package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project

import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.RunConfigurationExecutorInfo
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.RunConfigurationInfo
import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerEx
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.project.Project
import java.lang.reflect.Method

internal object RunConfigurationSupport {
    const val DEFAULT_TIMEOUT_MS = 20_000
    const val DEFAULT_MAX_LINES_COUNT = 200

    enum class TruncateMode(val wireValue: String) {
        START("start"),
        MIDDLE("middle"),
        END("end"),
        NONE("none");

        companion object {
            fun fromWireValue(value: String?): TruncateMode? {
                return entries.find { it.wireValue == value }
            }
        }
    }

    enum class WaitForMode(val wireValue: String) {
        STARTED("started"),
        FIRST_OUTPUT("first_output"),
        COMPLETED("completed");

        companion object {
            fun fromWireValue(value: String?): WaitForMode? {
                return entries.find { it.wireValue == value }
            }
        }
    }

    enum class WaitOutcome(val wireValue: String) {
        STARTED("started"),
        FIRST_OUTPUT("first_output"),
        COMPLETED("completed"),
        TIMEOUT("timeout")
    }

    enum class TerminationReason(val wireValue: String) {
        COMPLETED("completed"),
        STOPPED_BY_USER("stopped_by_user"),
        NON_ZERO_EXIT("non_zero_exit"),
        VALIDATION_FAILED("validation_failed"),
        LAUNCH_FAILED("launch_failed")
    }

    data class TruncatedOutput(
        val text: String,
        val truncated: Boolean
    )

    data class ResolutionResult(
        val settings: RunnerAndConfigurationSettings? = null,
        val error: String? = null
    )

    fun getAllSettings(project: Project): List<RunnerAndConfigurationSettings> {
        return RunManager.getInstance(project)
            .allSettings
            .filterNot { it.isTemplate }
            .sortedWith(
                compareBy<RunnerAndConfigurationSettings>(
                    { it.name.lowercase() },
                    { (it.folderName ?: "").lowercase() }
                )
            )
    }

    fun getAvailableExecutors(project: Project, settings: RunnerAndConfigurationSettings): List<Executor> {
        return ExecutorRegistry.getInstance()
            .registeredExecutors
            .filter { executor ->
                executor.isApplicable(project) && ProgramRunnerUtil.getRunner(executor.id, settings) != null
            }
            .sortedBy { it.actionName.lowercase() }
    }

    fun toRunConfigurationInfo(
        project: Project,
        settings: RunnerAndConfigurationSettings,
        selectedConfigurationId: String?
    ): RunConfigurationInfo {
        val configuration = settings.configuration
        val availableExecutors = getAvailableExecutors(project, settings).map { executor ->
            RunConfigurationExecutorInfo(
                id = executor.id,
                actionName = executor.actionName
            )
        }
        val workingDirectory = extractWorkingDirectory(configuration)
        val mainClass = extractMainClass(configuration)
        val taskNames = extractTaskNames(configuration)
        val beforeRunTasks = RunManagerEx.getInstanceEx(project).getBeforeRunTasks(configuration)
            .mapNotNull { task -> formatBeforeRunTask(task) }

        return RunConfigurationInfo(
            id = settings.uniqueID,
            name = settings.name,
            typeId = settings.type.id,
            typeDisplayName = settings.type.displayName.toString(),
            folderName = settings.folderName,
            isTemporary = settings.isTemporary,
            isShared = settings.isShared(),
            isSelected = settings.uniqueID == selectedConfigurationId,
            availableExecutors = availableExecutors,
            workingDirectory = workingDirectory,
            mainClass = mainClass,
            taskNames = taskNames,
            beforeRunTasks = beforeRunTasks
        )
    }

    internal fun extractWorkingDirectory(configuration: Any): String? {
        return extractStringDetailByChains(
            configuration,
            listOf("getWorkingDirectory"),
            listOf("getWorkingDir"),
            listOf("getWorkDirectory"),
            listOf("getRawWorkingDirectory"),
            listOf("getSettings", "getExternalProjectPath")
        )
    }

    internal fun extractMainClass(configuration: Any): String? {
        return extractStringDetailByChains(
            configuration,
            listOf("getMainClassName"),
            listOf("getRunClass"),
            listOf("getRunClassName"),
            listOf("getMainClass")
        )
    }

    internal fun extractTaskNames(configuration: Any): List<String> {
        return extractStringListDetailByChains(
            configuration,
            listOf("getTaskNames"),
            listOf("getTasks"),
            listOf("getSettings", "getTaskNames"),
            listOf("getCommandLine", "getTasks", "getTokens")
        )
    }

    fun resolve(project: Project, id: String?, name: String?): ResolutionResult {
        val allSettings = getAllSettings(project)

        val configurationId = id?.trim()?.takeIf { it.isNotEmpty() }
        if (configurationId != null) {
            val settings = allSettings.find { it.uniqueID == configurationId }
                ?: return ResolutionResult(error = "Run configuration not found for id '$configurationId'.")
            return ResolutionResult(settings = settings)
        }

        val configurationName = name?.trim()
            ?: return ResolutionResult(error = "Missing required parameter: id or name")

        if (configurationName.isEmpty()) {
            return ResolutionResult(error = "Parameter 'name' must not be blank.")
        }

        val matches = allSettings.filter { it.name == configurationName }
        return when (matches.size) {
            0 -> ResolutionResult(error = "Run configuration not found for name '$configurationName'.")
            1 -> ResolutionResult(settings = matches.single())
            else -> {
                val matchingIds = matches.joinToString(", ") { it.uniqueID }
                ResolutionResult(
                    error = "Multiple run configurations found for name '$configurationName'. Use 'id' instead. Matching ids: $matchingIds"
                )
            }
        }
    }

    fun formatExecutorIds(executors: List<Executor>): String {
        return if (executors.isEmpty()) {
            "none"
        } else {
            executors.joinToString(", ") { it.id }
        }
    }

    fun truncateOutput(output: String, maxLinesCount: Int, mode: TruncateMode): TruncatedOutput {
        if (mode == TruncateMode.NONE) {
            return TruncatedOutput(text = output, truncated = false)
        }

        val lines = output.lineSequence().toList()
        if (lines.size <= maxLinesCount) {
            return TruncatedOutput(text = output, truncated = false)
        }

        if (maxLinesCount == 1) {
            return TruncatedOutput(
                text = omittedLinesMarker(lines.size),
                truncated = true
            )
        }

        return when (mode) {
            TruncateMode.START -> {
                val keptLineCount = maxLinesCount - 1
                val keptLines = lines.takeLast(keptLineCount)
                val omittedCount = lines.size - keptLineCount
                TruncatedOutput(
                    text = (listOf(omittedLinesMarker(omittedCount)) + keptLines).joinToString("\n"),
                    truncated = true
                )
            }

            TruncateMode.MIDDLE -> {
                val remainingLineBudget = maxLinesCount - 1
                val headCount = remainingLineBudget / 2
                val tailCount = remainingLineBudget - headCount
                val headLines = lines.take(headCount)
                val tailLines = lines.takeLast(tailCount)
                val omittedCount = lines.size - headCount - tailCount
                TruncatedOutput(
                    text = (headLines + omittedLinesMarker(omittedCount) + tailLines).joinToString("\n"),
                    truncated = true
                )
            }

            TruncateMode.END -> {
                val keptLineCount = maxLinesCount - 1
                val keptLines = lines.take(keptLineCount)
                val omittedCount = lines.size - keptLineCount
                TruncatedOutput(
                    text = (keptLines + omittedLinesMarker(omittedCount)).joinToString("\n"),
                    truncated = true
                )
            }

            TruncateMode.NONE -> TruncatedOutput(text = output, truncated = false)
        }
    }

    private fun omittedLinesMarker(omittedCount: Int): String {
        val suffix = if (omittedCount == 1) "" else "s"
        return "... [$omittedCount line$suffix omitted] ..."
    }

    private fun extractStringDetailByChains(target: Any, vararg methodChains: List<String>): String? {
        return methodChains.asSequence()
            .mapNotNull { methodChain -> invokeMethodChain(target, methodChain) }
            .mapNotNull { value -> value.toString().trim().takeIf { it.isNotEmpty() } }
            .firstOrNull()
    }

    private fun extractStringListDetailByChains(target: Any, vararg methodChains: List<String>): List<String> {
        return methodChains.asSequence()
            .mapNotNull { methodChain -> invokeMethodChain(target, methodChain) }
            .mapNotNull { value -> normalizeStringList(value) }
            .firstOrNull()
            ?: emptyList()
    }

    private fun invokeMethodChain(target: Any, methodChain: List<String>): Any? {
        var current: Any? = target
        for (methodName in methodChain) {
            current ?: return null
            val method = findZeroArgMethod(current.javaClass, methodName) ?: return null
            current = runCatching { method.invoke(current) }.getOrNull()
        }
        return current
    }

    private fun formatBeforeRunTask(task: Any): String? {
        val providerId = runCatching {
            val method = findZeroArgMethod(task.javaClass, "getProviderId")
            method?.invoke(task)?.toString()
        }.getOrNull()
        val enabled = runCatching {
            val method = findZeroArgMethod(task.javaClass, "isEnabled")
            method?.invoke(task) as? Boolean ?: true
        }.getOrDefault(true)
        if (!enabled) {
            return null
        }

        val label = providerId?.takeIf { it.isNotBlank() } ?: task.javaClass.simpleName
        return label.replace("Key: ", "")
    }

    private fun normalizeStringList(value: Any): List<String>? {
        val values = when (value) {
            is Array<*> -> value.toList()
            is Iterable<*> -> value.toList()
            else -> return null
        }

        return values.mapNotNull { item ->
            item?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun findZeroArgMethod(targetClass: Class<*>, methodName: String): Method? {
        return targetClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterCount == 0
        }
    }
}
