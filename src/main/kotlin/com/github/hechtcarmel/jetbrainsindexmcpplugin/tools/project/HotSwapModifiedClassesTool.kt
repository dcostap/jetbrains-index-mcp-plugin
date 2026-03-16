package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ToolNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.HotSwapModifiedClassesResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.HotSwapManager
import com.intellij.debugger.impl.HotSwapProgress
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.compiler.CompileStatusNotification
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.resume

class HotSwapModifiedClassesTool : AbstractMcpTool() {

    override val name = ToolNames.HOTSWAP_MODIFIED_CLASSES

    override val description = """
        Compile dirty Java classes and hot-swap the modified bytecode into active Java debug sessions.

        This is the headless equivalent of IntelliJ's "Compile and Reload Modified Files" action. Requires the Java plugin and at least one active Java debug session.

        Parameters: project_path (optional, only needed with multiple projects open).

        Example: {}
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .build()

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val sessions = edtAction {
            DebuggerManagerEx.getInstanceEx(project)
                .sessions
                .filter { it.isAttached && !it.isStopped }
                .sortedBy { it.sessionName.lowercase() }
        }

        if (sessions.isEmpty()) {
            return createErrorResult("No active Java debug sessions found.")
        }

        val compileResult = compileDirtyClasses(project)
        if (compileResult.aborted) {
            return createErrorResult("Compilation was aborted before hot swap could run.")
        }
        if (compileResult.errors > 0) {
            return createErrorResult(
                "Compilation failed with ${compileResult.errors} error(s) and ${compileResult.warnings} warning(s). Hot swap was not attempted."
            )
        }

        val progress = CollectingHotSwapProgress(project)
        val modifiedClasses = edtAction {
            HotSwapManager.scanForModifiedClasses(sessions, progress)
        }
        val reloadedClassCount = modifiedClasses.values.sumOf { it.size }

        if (reloadedClassCount == 0) {
            return createJsonResult(
                HotSwapModifiedClassesResult(
                    compiled = true,
                    reloaded = false,
                    debugSessionCount = sessions.size,
                    reloadedClassCount = 0,
                    compilationErrors = compileResult.errors,
                    compilationWarnings = compileResult.warnings,
                    sessions = sessions.map { it.sessionName },
                    messages = progress.messages,
                    message = "Compilation succeeded, but no modified classes were found for hot swap."
                )
            )
        }

        edtAction {
            HotSwapManager.reloadModifiedClasses(modifiedClasses, progress)
            progress.finished()
        }

        return createJsonResult(
            HotSwapModifiedClassesResult(
                compiled = true,
                reloaded = true,
                debugSessionCount = sessions.size,
                reloadedClassCount = reloadedClassCount,
                compilationErrors = compileResult.errors,
                compilationWarnings = compileResult.warnings,
                sessions = sessions.map { it.sessionName },
                messages = progress.messages,
                message = "Reloaded $reloadedClassCount modified class(es) into ${sessions.size} debug session(s)."
            )
        )
    }

    private suspend fun compileDirtyClasses(project: Project): CompileResult {
        return suspendCancellableCoroutine { continuation ->
            val startCompilation = Runnable {
                if (project.isDisposed) {
                    if (continuation.isActive) {
                        continuation.resume(CompileResult(aborted = true, errors = 0, warnings = 0))
                    }
                    return@Runnable
                }

                CompilerManager.getInstance(project).make(object : CompileStatusNotification {
                    override fun finished(
                        aborted: Boolean,
                        errors: Int,
                        warnings: Int,
                        compileContext: com.intellij.openapi.compiler.CompileContext
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(CompileResult(aborted = aborted, errors = errors, warnings = warnings))
                        }
                    }
                })
            }

            val application = ApplicationManager.getApplication()
            if (application.isDispatchThread) {
                startCompilation.run()
            } else {
                application.invokeLater(startCompilation, ModalityState.any())
            }
        }
    }

    private data class CompileResult(
        val aborted: Boolean,
        val errors: Int,
        val warnings: Int
    )

    private class CollectingHotSwapProgress(project: Project) : HotSwapProgress(project) {
        val messages = mutableListOf<String>()

        override fun addMessage(session: DebuggerSession, type: Int, text: String) {
            messages.add("${session.sessionName}: $text")
        }

        override fun setText(text: String) {
            if (text.isNotBlank()) {
                messages.add(text)
            }
        }

        override fun setTitle(text: String) {
            if (text.isNotBlank()) {
                messages.add(text)
            }
        }

        override fun setFraction(fraction: Double) {
        }

        override fun setDebuggerSession(debuggerSession: DebuggerSession) {
        }
    }
}
