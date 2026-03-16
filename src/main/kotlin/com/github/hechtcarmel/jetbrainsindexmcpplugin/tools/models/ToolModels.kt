package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models

import kotlinx.serialization.Serializable

@Serializable
data class PositionInput(
    val file: String,
    val line: Int,
    val column: Int
)

// find_usages output
@Serializable
data class UsageLocation(
    val file: String,
    val line: Int,
    val column: Int,
    val context: String,
    val type: String
)

@Serializable
data class FindUsagesResult(
    val usages: List<UsageLocation>,
    val totalCount: Int,
    val truncated: Boolean = false
)

// find_definition output
@Serializable
data class DefinitionResult(
    val file: String,
    val line: Int,
    val column: Int,
    val preview: String,
    val symbolName: String
)

// ide_read_file output
@Serializable
data class ReadFileResult(
    val file: String,
    val content: String,
    val language: String?,
    val lineCount: Int,
    val startLine: Int?,
    val endLine: Int?,
    val isLibraryFile: Boolean
)


// type_hierarchy output
@Serializable
data class TypeHierarchyResult(
    val element: TypeElement,
    val supertypes: List<TypeElement>,
    val subtypes: List<TypeElement>
)

@Serializable
data class TypeElement(
    val name: String,
    val file: String?,
    val kind: String,
    val language: String? = null,
    val supertypes: List<TypeElement>? = null
)

// call_hierarchy output
@Serializable
data class CallHierarchyResult(
    val element: CallElement,
    val calls: List<CallElement>
)

@Serializable
data class CallElement(
    val name: String,
    val file: String,
    val line: Int,
    val column: Int,
    val language: String? = null,
    val children: List<CallElement>? = null
)

// find_implementations output
@Serializable
data class ImplementationResult(
    val implementations: List<ImplementationLocation>,
    val totalCount: Int
)

@Serializable
data class ImplementationLocation(
    val name: String,
    val file: String,
    val line: Int,
    val column: Int,
    val kind: String,
    val language: String? = null
)


// ide_diagnostics output
@Serializable
data class DiagnosticsResult(
    val problems: List<ProblemInfo>,
    val intentions: List<IntentionInfo>,
    val problemCount: Int,
    val intentionCount: Int
)

@Serializable
data class ProblemInfo(
    val message: String,
    val severity: String,
    val file: String,
    val line: Int,
    val column: Int,
    val endLine: Int?,
    val endColumn: Int?
)

@Serializable
data class IntentionInfo(
    val name: String,
    val description: String?
)

// Refactoring result
@Serializable
data class RefactoringResult(
    val success: Boolean,
    val affectedFiles: List<String>,
    val changesCount: Int,
    val message: String
)


// get_index_status output
@Serializable
data class IndexStatusResult(
    val isDumbMode: Boolean,
    val isIndexing: Boolean,
    val indexingProgress: Double?
)

// ide_list_run_configurations output
@Serializable
data class ListRunConfigurationsResult(
    val runConfigurations: List<RunConfigurationInfo>,
    val totalCount: Int
)

@Serializable
data class RunConfigurationInfo(
    val id: String,
    val name: String,
    val typeId: String,
    val typeDisplayName: String,
    val folderName: String? = null,
    val isTemporary: Boolean,
    val isShared: Boolean,
    val isSelected: Boolean,
    val availableExecutors: List<RunConfigurationExecutorInfo>,
    val workingDirectory: String? = null,
    val mainClass: String? = null,
    val taskNames: List<String> = emptyList(),
    val beforeRunTasks: List<String> = emptyList()
)

@Serializable
data class RunConfigurationExecutorInfo(
    val id: String,
    val actionName: String
)

// ide_run_configuration output
@Serializable
data class RunConfigurationExecutionResult(
    val executionId: String,
    val id: String,
    val name: String,
    val executorId: String,
    val waitFor: String,
    val waitOutcome: String,
    val started: Boolean,
    val completed: Boolean,
    val timedOut: Boolean,
    val success: Boolean? = null,
    val exitCode: Int? = null,
    val terminationReason: String? = null,
    val output: String = "",
    val outputLength: Int,
    val lastChunkLength: Int,
    val truncated: Boolean = false,
    val timeoutMs: Int,
    val message: String
)

@Serializable
data class RunExecutionStatusResult(
    val executionId: String,
    val id: String,
    val name: String,
    val executorId: String,
    val status: String,
    val running: Boolean,
    val completed: Boolean,
    val stopRequested: Boolean,
    val success: Boolean? = null,
    val exitCode: Int? = null,
    val terminationReason: String? = null,
    val outputOffset: Int,
    val outputLength: Int,
    val lastChunkLength: Int,
    val startedAtMs: Long,
    val finishedAtMs: Long? = null,
    val message: String? = null
)

@Serializable
data class RunExecutionOutputResult(
    val executionId: String,
    val status: String,
    val completed: Boolean,
    val success: Boolean? = null,
    val exitCode: Int? = null,
    val terminationReason: String? = null,
    val since: Int,
    val nextOffset: Int,
    val outputLength: Int,
    val lastChunkLength: Int,
    val output: String
)

@Serializable
data class StopRunExecutionResult(
    val executionId: String,
    val stopRequested: Boolean,
    val wasRunning: Boolean,
    val completed: Boolean,
    val success: Boolean? = null,
    val exitCode: Int? = null,
    val terminationReason: String? = null,
    val waitOutcome: String,
    val message: String
)

// ide_hotswap_modified_classes output
@Serializable
data class HotSwapModifiedClassesResult(
    val compiled: Boolean,
    val reloaded: Boolean,
    val debugSessionCount: Int,
    val reloadedClassCount: Int,
    val compilationErrors: Int,
    val compilationWarnings: Int,
    val sessions: List<String>,
    val messages: List<String>,
    val message: String
)

// ide_find_symbol output
@Serializable
data class FindSymbolResult(
    val symbols: List<SymbolMatch>,
    val totalCount: Int,
    val query: String
)

@Serializable
data class SymbolMatch(
    val name: String,
    val qualifiedName: String?,
    val kind: String,
    val file: String,
    val line: Int,
    val column: Int,
    val containerName: String?,
    val language: String? = null
)

// ide_find_super_methods output
@Serializable
data class SuperMethodsResult(
    val method: MethodInfo,
    val hierarchy: List<SuperMethodInfo>,
    val totalCount: Int
)

@Serializable
data class MethodInfo(
    val name: String,
    val signature: String,
    val containingClass: String,
    val file: String,
    val line: Int,
    val column: Int,
    val language: String? = null
)

@Serializable
data class SuperMethodInfo(
    val name: String,
    val signature: String,
    val containingClass: String,
    val containingClassKind: String,
    val file: String?,
    val line: Int?,
    val column: Int?,
    val isInterface: Boolean,
    val depth: Int,
    val language: String? = null
)

// ide_find_class output (reuses SymbolMatch)
@Serializable
data class FindClassResult(
    val classes: List<SymbolMatch>,
    val totalCount: Int,
    val query: String
)

// ide_find_file output
@Serializable
data class FindFileResult(
    val files: List<FileMatch>,
    val totalCount: Int,
    val query: String
)

@Serializable
data class FileMatch(
    val name: String,
    val path: String,
    val directory: String
)

// ide_get_active_file output
@Serializable
data class ActiveFileInfo(
    val file: String,
    val line: Int?,
    val column: Int?,
    val selectedText: String?,
    val hasSelection: Boolean,
    val language: String?
)

@Serializable
data class GetActiveFileResult(
    val activeFiles: List<ActiveFileInfo>
)

// ide_open_file output
@Serializable
data class OpenFileResult(
    val file: String,
    val opened: Boolean,
    val message: String
)

// ide_search_text output
@Serializable
data class SearchTextResult(
    val matches: List<TextMatch>,
    val totalCount: Int,
    val query: String
)

@Serializable
data class TextMatch(
    val file: String,
    val line: Int,
    val column: Int,
    val context: String,       // line content
    val contextType: String    // "CODE", "COMMENT", "STRING_LITERAL"
)
