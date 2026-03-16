package com.github.hechtcarmel.jetbrainsindexmcpplugin.constants

object ToolNames {
    // Navigation tools
    const val FIND_REFERENCES = "ide_find_references"
    const val FIND_DEFINITION = "ide_find_definition"
    const val TYPE_HIERARCHY = "ide_type_hierarchy"
    const val CALL_HIERARCHY = "ide_call_hierarchy"
    const val FIND_IMPLEMENTATIONS = "ide_find_implementations"
    const val FIND_SYMBOL = "ide_find_symbol"
    const val FIND_SUPER_METHODS = "ide_find_super_methods"
    const val FILE_STRUCTURE = "ide_file_structure"
    const val FIND_CLASS = "ide_find_class"
    const val FIND_FILE = "ide_find_file"
    const val SEARCH_TEXT = "ide_search_text"
    const val READ_FILE = "ide_read_file"

    // Intelligence tools
    const val DIAGNOSTICS = "ide_diagnostics"

    // Project tools
    const val HOTSWAP_MODIFIED_CLASSES = "ide_hotswap_modified_classes"
    const val INDEX_STATUS = "ide_index_status"
    const val LIST_RUN_CONFIGURATIONS = "ide_list_run_configurations"
    const val GET_RUN_EXECUTION = "ide_get_run_execution"
    const val READ_RUN_OUTPUT = "ide_read_run_output"
    const val RUN_CONFIGURATION = "ide_run_configuration"
    const val STOP_RUN_EXECUTION = "ide_stop_run_execution"

    // Refactoring tools
    const val REFACTOR_RENAME = "ide_refactor_rename"
    const val REFACTOR_SAFE_DELETE = "ide_refactor_safe_delete"
    const val REFORMAT_CODE = "ide_reformat_code"

    // Editor tools
    const val GET_ACTIVE_FILE = "ide_get_active_file"
    const val OPEN_FILE = "ide_open_file"

    /**
     * All known tool names, sorted alphabetically.
     * Keep this list in sync when adding or removing tool name constants.
     */
    val ALL: List<String> = listOf(
        CALL_HIERARCHY,
        DIAGNOSTICS,
        FILE_STRUCTURE,
        FIND_CLASS,
        FIND_DEFINITION,
        FIND_FILE,
        FIND_IMPLEMENTATIONS,
        FIND_REFERENCES,
        FIND_SUPER_METHODS,
        FIND_SYMBOL,
        GET_ACTIVE_FILE,
        GET_RUN_EXECUTION,
        HOTSWAP_MODIFIED_CLASSES,
        INDEX_STATUS,
        LIST_RUN_CONFIGURATIONS,
        OPEN_FILE,
        READ_FILE,
        READ_RUN_OUTPUT,
        REFACTOR_RENAME,
        REFACTOR_SAFE_DELETE,
        REFORMAT_CODE,
        RUN_CONFIGURATION,
        SEARCH_TEXT,
        STOP_RUN_EXECUTION,
        TYPE_HIERARCHY
    )
}
