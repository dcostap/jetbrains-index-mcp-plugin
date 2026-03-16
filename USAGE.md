# IDE Index MCP Server - Tool Reference

This document provides detailed documentation for all MCP tools available in the IDE Index MCP Server plugin.

## Tool Availability by IDE

Tools are organized into two categories based on IDE compatibility:

### Universal Tools (All JetBrains IDEs)

These tools work in **every** JetBrains IDE:

| Tool | Description |
|------|-------------|
| `ide_find_references` | Find all references to a symbol |
| `ide_find_definition` | Find symbol definition location |
| `ide_diagnostics` | Analyze code for problems and intentions |
| `ide_index_status` | Check indexing status |
| `ide_list_run_configurations` | List run configurations and extracted details |
| `ide_run_configuration` | Run a run configuration with `waitFor` modes, bounded output capture, and an execution id |
| `ide_get_run_execution` | Get status for a tracked run execution |
| `ide_read_run_output` | Read incremental output from a tracked run execution with output-length metadata |
| `ide_stop_run_execution` | Stop a tracked run execution, optionally waiting for shutdown |
| `ide_refactor_rename` | Rename symbol with reference updates (all languages) |

### Extended Tools (Language-Aware)

These tools activate based on available language plugins:

| Tool | Description | Languages |
|------|-------------|-----------|
| `ide_type_hierarchy` | Get type inheritance hierarchy | Java, Kotlin, Python, JS/TS, PHP, Rust |
| `ide_call_hierarchy` | Analyze method call relationships | Java, Kotlin, Python, JS/TS, PHP, Rust |
| `ide_find_implementations` | Find interface implementations | Java, Kotlin, Python, JS/TS, PHP, Rust |
| `ide_find_symbol` | Search symbols by name | Java, Kotlin, Python, JS/TS, PHP, Rust |
| `ide_find_super_methods` | Find overridden methods | Java, Kotlin, Python, JS/TS, PHP, Rust |

### Refactoring Tools (Java/Kotlin Only)

| Tool | Description |
|------|-------------|
| `ide_refactor_safe_delete` | Safely delete with usage check |

### Java Debugger Tools

| Tool | Description |
|------|-------------|
| `ide_hotswap_modified_classes` | Compile dirty classes and hot-swap them into active Java debug sessions |

---

## Table of Contents

- [Common Parameters](#common-parameters)
- [Universal Tools](#universal-tools)
  - [ide_find_references](#ide_find_references)
  - [ide_find_definition](#ide_find_definition)
  - [ide_diagnostics](#ide_diagnostics)
  - [ide_index_status](#ide_index_status)
  - [ide_list_run_configurations](#ide_list_run_configurations)
  - [ide_run_configuration](#ide_run_configuration)
  - [ide_get_run_execution](#ide_get_run_execution)
  - [ide_read_run_output](#ide_read_run_output)
  - [ide_stop_run_execution](#ide_stop_run_execution)
  - [ide_refactor_rename](#ide_refactor_rename)
- [Extended Tools (Language-Aware)](#extended-tools-language-aware)
  - [ide_type_hierarchy](#ide_type_hierarchy)
  - [ide_call_hierarchy](#ide_call_hierarchy)
  - [ide_find_implementations](#ide_find_implementations)
  - [ide_find_symbol](#ide_find_symbol)
  - [ide_find_super_methods](#ide_find_super_methods)
- [Java-Specific Refactoring Tools](#java-specific-refactoring-tools)
  - [ide_refactor_safe_delete](#ide_refactor_safe_delete)
- [Java Debugger Tools](#java-debugger-tools)
  - [ide_hotswap_modified_classes](#ide_hotswap_modified_classes)
- [Error Handling](#error-handling)

---

## Common Parameters

All tools accept an optional `project_path` parameter:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `project_path` | string | No | Absolute path to the project root. Required when multiple projects are open in the IDE. For workspace projects, use the sub-project path. |

### Position Parameters

Most tools operate on a specific location in code and require these parameters:

| Parameter | Type | Description |
|-----------|------|-------------|
| `file` | string | Path to the file relative to project root (e.g., `src/main/java/MyClass.java`) |
| `line` | integer | 1-based line number |
| `column` | integer | 1-based column number |

---

## Universal Tools

These tools work in all JetBrains IDEs (IntelliJ, PyCharm, WebStorm, GoLand, etc.).

### ide_find_references

Finds all references to a symbol across the entire project using IntelliJ's semantic index.

**Use when:**
- Locating where a method, class, variable, or field is called or accessed
- Understanding code dependencies
- Preparing for refactoring

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file` | string | Yes | Path to the file relative to project root |
| `line` | integer | Yes | 1-based line number |
| `column` | integer | Yes | 1-based column number |
| `maxResults` | integer | No | Maximum number of references to return (default: 100, max: 500) |

**Example Request:**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_find_references",
    "arguments": {
      "file": "src/main/java/com/example/UserService.java",
      "line": 15,
      "column": 20
    }
  }
}
```

**Example Response:**

```json
{
  "usages": [
    {
      "file": "src/main/java/com/example/UserController.java",
      "line": 42,
      "column": 15,
      "context": "userService.findUser(id)",
      "type": "METHOD_CALL"
    },
    {
      "file": "src/test/java/com/example/UserServiceTest.java",
      "line": 28,
      "column": 10,
      "context": "service.findUser(\"test\")",
      "type": "METHOD_CALL"
    }
  ],
  "totalCount": 2
}
```

**Reference Types:**
- `METHOD_CALL` - Method invocation
- `FIELD_ACCESS` - Field read/write
- `REFERENCE` - General reference
- `IMPORT` - Import statement
- `PARAMETER` - Method parameter
- `VARIABLE` - Variable usage

---

### ide_find_definition

Finds the definition/declaration location of a symbol at a given source location.

**Use when:**
- Understanding where a method, class, variable, or field is declared
- Looking up the original definition from a usage site

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file` | string | Yes | Path to the file relative to project root |
| `line` | integer | Yes | 1-based line number |
| `column` | integer | Yes | 1-based column number |

**Example Request:**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_find_definition",
    "arguments": {
      "file": "src/main/java/com/example/App.java",
      "line": 25,
      "column": 12
    }
  }
}
```

**Example Response:**

```json
{
  "file": "src/main/java/com/example/UserService.java",
  "line": 15,
  "column": 17,
  "preview": "14:     \n15:     public User findUser(String id) {\n16:         return userRepository.findById(id);\n17:     }",
  "symbolName": "findUser"
}
```

---

## Extended Tools (Language-Aware)

These tools activate based on available language plugins:
- **Java/Kotlin** - IntelliJ IDEA, Android Studio
- **Python** - PyCharm (all editions), IntelliJ with Python plugin
- **JavaScript/TypeScript** - WebStorm, IntelliJ Ultimate, PhpStorm
- **PHP** - PhpStorm, IntelliJ Ultimate with PHP plugin
- **Rust** - RustRover, IntelliJ Ultimate with Rust plugin, CLion

In IDEs without language-specific plugins (e.g., DataGrip), these tools will not appear in the tools list.

### ide_type_hierarchy

Retrieves the complete type hierarchy for a class or interface.

**Use when:**
- Exploring class inheritance chains
- Understanding polymorphism
- Finding all subclasses or implementations
- Analyzing interface implementations

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file` | string | No* | Path to the file relative to project root |
| `line` | integer | No* | 1-based line number |
| `column` | integer | No* | 1-based column number |
| `className` | string | No* | Fully qualified class name (alternative to position) |

*Either `file`/`line`/`column` OR `className` must be provided.

**Example Request (by position):**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_type_hierarchy",
    "arguments": {
      "file": "src/main/java/com/example/ArrayList.java",
      "line": 5,
      "column": 14
    }
  }
}
```

**Example Request (by class name - Java):**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_type_hierarchy",
    "arguments": {
      "className": "java.util.ArrayList"
    }
  }
}
```

**Example Request (by class name - PHP):**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_type_hierarchy",
    "arguments": {
      "className": "App\\Models\\User"
    }
  }
}
```

**Example Response:**

```json
{
  "element": {
    "name": "com.example.UserServiceImpl",
    "file": "src/main/java/com/example/UserServiceImpl.java",
    "kind": "CLASS"
  },
  "supertypes": [
    {
      "name": "com.example.UserService",
      "file": "src/main/java/com/example/UserService.java",
      "kind": "INTERFACE"
    },
    {
      "name": "com.example.BaseService",
      "file": "src/main/java/com/example/BaseService.java",
      "kind": "ABSTRACT_CLASS"
    }
  ],
  "subtypes": [
    {
      "name": "com.example.AdminUserServiceImpl",
      "file": "src/main/java/com/example/AdminUserServiceImpl.java",
      "kind": "CLASS"
    }
  ]
}
```

**Kind Values:**
- `CLASS` - Concrete class
- `ABSTRACT_CLASS` - Abstract class
- `INTERFACE` - Interface
- `ENUM` - Enum type
- `ANNOTATION` - Annotation type
- `RECORD` - Record class (Java 16+)

---

### ide_call_hierarchy

Analyzes method call relationships to find callers or callees.

**Use when:**
- Tracing execution flow
- Understanding code dependencies
- Analyzing impact of method changes
- Debugging to understand how a method is reached

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file` | string | Yes | Path to the file relative to project root |
| `line` | integer | Yes | 1-based line number |
| `column` | integer | Yes | 1-based column number |
| `direction` | string | Yes | `"callers"` or `"callees"` |
| `depth` | integer | No | How deep to traverse (default: 3, max: 5) |

**Example Request:**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_call_hierarchy",
    "arguments": {
      "file": "src/main/java/com/example/UserService.java",
      "line": 20,
      "column": 10,
      "direction": "callers"
    }
  }
}
```

**Example Response:**

```json
{
  "element": {
    "name": "UserService.validateUser(String)",
    "file": "src/main/java/com/example/UserService.java",
    "line": 20,
    "column": 17
  },
  "calls": [
    {
      "name": "UserController.createUser(UserRequest)",
      "file": "src/main/java/com/example/UserController.java",
      "line": 45,
      "column": 17
    },
    {
      "name": "UserController.updateUser(String, UserRequest)",
      "file": "src/main/java/com/example/UserController.java",
      "line": 62,
      "column": 17
    }
  ]
}
```

---

### ide_find_implementations

Finds all concrete implementations of an interface, abstract class, or abstract method.

**Use when:**
- Locating classes that implement an interface
- Finding classes that extend an abstract class
- Finding all overriding methods for polymorphic behavior analysis

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file` | string | Yes | Path to the file relative to project root |
| `line` | integer | Yes | 1-based line number |
| `column` | integer | Yes | 1-based column number |

**Example Request:**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_find_implementations",
    "arguments": {
      "file": "src/main/java/com/example/Repository.java",
      "line": 8,
      "column": 10
    }
  }
}
```

**Example Response:**

```json
{
  "implementations": [
    {
      "name": "com.example.JpaUserRepository",
      "file": "src/main/java/com/example/JpaUserRepository.java",
      "line": 12,
      "column": 14,
      "kind": "CLASS"
    },
    {
      "name": "com.example.InMemoryUserRepository",
      "file": "src/main/java/com/example/InMemoryUserRepository.java",
      "line": 8,
      "column": 14,
      "kind": "CLASS"
    }
  ],
  "totalCount": 2
}
```

---

### ide_find_symbol

Searches for code symbols (classes, interfaces, methods, fields) by name using the IDE's semantic index.

**Use when:**
- Finding a class or interface by name (e.g., find "UserService")
- Locating methods across the codebase (e.g., find all "findById" methods)
- Discovering fields or constants by name
- Navigating to code when you know the symbol name but not the file location

**Supports fuzzy matching:**
- Substring: "Service" matches "UserService", "OrderService"
- CamelCase: "USvc" matches "UserService", "US" matches "UserService"

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `query` | string | Yes | Search pattern (supports substring and camelCase matching) |
| `includeLibraries` | boolean | No | Include symbols from library dependencies (default: false) |
| `limit` | integer | No | Maximum results to return (default: 25, max: 100) |

**Example Request:**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_find_symbol",
    "arguments": {
      "query": "UserService"
    }
  }
}
```

**Example Request (camelCase matching):**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_find_symbol",
    "arguments": {
      "query": "USvc",
      "includeLibraries": true,
      "limit": 50
    }
  }
}
```

**Example Response:**

```json
{
  "symbols": [
    {
      "name": "UserService",
      "qualifiedName": "com.example.service.UserService",
      "kind": "INTERFACE",
      "file": "src/main/java/com/example/service/UserService.java",
      "line": 12,
      "column": 18,
      "containerName": null
    },
    {
      "name": "UserServiceImpl",
      "qualifiedName": "com.example.service.UserServiceImpl",
      "kind": "CLASS",
      "file": "src/main/java/com/example/service/UserServiceImpl.java",
      "line": 15,
      "column": 14,
      "containerName": null
    },
    {
      "name": "findUser",
      "qualifiedName": "com.example.service.UserService.findUser",
      "kind": "METHOD",
      "file": "src/main/java/com/example/service/UserService.java",
      "line": 18,
      "column": 10,
      "containerName": "UserService"
    }
  ],
  "totalCount": 3,
  "query": "UserService"
}
```

**Kind Values:**
- `CLASS` - Concrete class
- `ABSTRACT_CLASS` - Abstract class
- `INTERFACE` - Interface
- `ENUM` - Enum type
- `ANNOTATION` - Annotation type
- `RECORD` - Record class (Java 16+)
- `METHOD` - Method
- `FIELD` - Field or constant

---

### ide_find_super_methods

Finds the complete inheritance hierarchy for a method - all parent methods it overrides or implements.

**Use when:**
- Finding which interface method an implementation overrides
- Navigating to the original method declaration in a parent class
- Understanding the full inheritance chain for a method with @Override
- Seeing all levels of method overriding (not just immediate parent)

**Position flexibility:** The position (line/column) can be anywhere within the method - on the name, inside the body, or on the @Override annotation. The tool automatically finds the enclosing method.

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file` | string | Yes | Path to the file relative to project root |
| `line` | integer | Yes | 1-based line number (any line within the method) |
| `column` | integer | Yes | 1-based column number (any position within the method) |

**Example Request:**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_find_super_methods",
    "arguments": {
      "file": "src/main/java/com/example/UserServiceImpl.java",
      "line": 25,
      "column": 10
    }
  }
}
```

**Example Response:**

```json
{
  "method": {
    "name": "findUser",
    "signature": "findUser(String id): User",
    "containingClass": "com.example.UserServiceImpl",
    "file": "src/main/java/com/example/UserServiceImpl.java",
    "line": 25,
    "column": 17
  },
  "hierarchy": [
    {
      "name": "findUser",
      "signature": "findUser(String id): User",
      "containingClass": "com.example.AbstractUserService",
      "containingClassKind": "ABSTRACT_CLASS",
      "file": "src/main/java/com/example/AbstractUserService.java",
      "line": 18,
      "column": 17,
      "isInterface": false,
      "depth": 1
    },
    {
      "name": "findUser",
      "signature": "findUser(String id): User",
      "containingClass": "com.example.UserService",
      "containingClassKind": "INTERFACE",
      "file": "src/main/java/com/example/UserService.java",
      "line": 12,
      "column": 10,
      "isInterface": true,
      "depth": 2
    }
  ],
  "totalCount": 2
}
```

**Depth field:** The `depth` field indicates the level in the hierarchy:
- `depth: 1` = immediate parent (first level up)
- `depth: 2` = grandparent (two levels up)
- And so on...

**containingClassKind Values:**
- `CLASS` - Concrete class
- `ABSTRACT_CLASS` - Abstract class
- `INTERFACE` - Interface

---

### ide_diagnostics

> **Availability**: Universal Tool - works in all JetBrains IDEs

Analyzes a file for code problems (errors, warnings) and available intentions/quick fixes.

**Use when:**
- Finding code issues in a file
- Checking code quality
- Identifying potential bugs
- Discovering available code improvements

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file` | string | Yes | Path to the file relative to project root |
| `line` | integer | No | 1-based line number for intention lookup (default: 1) |
| `column` | integer | No | 1-based column number for intention lookup (default: 1) |
| `startLine` | integer | No | Filter problems to start from this line |
| `endLine` | integer | No | Filter problems to end at this line |

**Example Request:**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_diagnostics",
    "arguments": {
      "file": "src/main/java/com/example/UserService.java"
    }
  }
}
```

**Example Response:**

```json
{
  "problems": [
    {
      "message": "Field 'logger' can be made final",
      "severity": "WARNING",
      "file": "src/main/java/com/example/UserService.java",
      "line": 8,
      "column": 12,
      "endLine": 8,
      "endColumn": 18
    },
    {
      "message": "Unused import 'java.util.Date'",
      "severity": "WARNING",
      "file": "src/main/java/com/example/UserService.java",
      "line": 3,
      "column": 1,
      "endLine": 3,
      "endColumn": 22
    }
  ],
  "intentions": [
    {
      "name": "Add 'final' modifier",
      "description": "Makes the field final"
    },
    {
      "name": "Optimize imports",
      "description": "Removes unused imports"
    }
  ],
  "problemCount": 2,
  "intentionCount": 2
}
```

**Severity Values:**
- `ERROR` - Compilation error
- `WARNING` - Potential problem
- `WEAK_WARNING` - Minor issue
- `INFO` - Informational

---

### ide_index_status

> **Availability**: Universal Tool - works in all JetBrains IDEs

Checks if the IDE is in dumb mode (indexing) or smart mode.

**Use when:**
- Checking if index-dependent operations will work
- Waiting for indexing to complete

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| (none) | | | No parameters required |

**Example Request:**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_index_status",
    "arguments": {}
  }
}
```

**Example Response:**

```json
{
  "isDumbMode": false,
  "isSmartMode": true,
  "isIndexing": false,
  "projectName": "my-application"
}
```

---

### ide_hotswap_modified_classes

> **Availability**: Java-specific tool - requires the Java plugin and at least one active Java debug session

Compiles dirty classes and hot-swaps any modified bytecode into active Java debug sessions.

**Use when:**
- You are debugging a Java application and want code changes reloaded without restarting
- You want a headless equivalent of IntelliJ's "Compile and Reload Modified Files" action

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| (none) | | | No parameters required |

**Example Request:**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_hotswap_modified_classes",
    "arguments": {}
  }
}
```

**Example Response:**

```json
{
  "compiled": true,
  "reloaded": true,
  "debugSessionCount": 1,
  "reloadedClassCount": 3,
  "compilationErrors": 0,
  "compilationWarnings": 0,
  "sessions": ["MyApp"],
  "messages": ["Scanning for modified classes", "Reloaded classes"],
  "message": "Reloaded 3 modified class(es) into 1 debug session(s)."
}
```

---

### ide_list_run_configurations

> **Availability**: Universal Tool - works in all JetBrains IDEs

Lists the run configurations available in the current project, including extracted execution details. For external-system configurations such as Gradle, `taskNames` are extracted from the nested external-system settings.

**Use when:**
- Discovering which run/debug targets are available
- Getting the stable configuration `id` required for reliable execution
- Checking which executors a configuration supports
- Inspecting concrete configuration details before deciding what to run

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| (none) | | | No parameters required |

**Example Request:**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_list_run_configurations",
    "arguments": {}
  }
}
```

**Example Response:**

```json
{
  "runConfigurations": [
    {
      "id": "Application:Demo",
      "name": "Demo",
      "typeId": "Application",
      "typeDisplayName": "Application",
      "folderName": null,
      "isTemporary": false,
      "isShared": false,
      "isSelected": true,
      "workingDirectory": "C:\\project",
      "mainClass": "com.example.DemoKt",
      "taskNames": [],
      "beforeRunTasks": [
        "CompileStepBeforeRun"
      ],
      "availableExecutors": [
        {
          "id": "Run",
          "actionName": "Run"
        },
        {
          "id": "Debug",
          "actionName": "Debug"
        }
      ]
    }
  ],
  "totalCount": 1
}
```

---

### ide_run_configuration

> **Availability**: Universal Tool - works in all JetBrains IDEs

Runs a run configuration by stable `id` or exact `name`, waits for a requested milestone up to a timeout, and returns captured output plus completion status. Every successful launch also returns an `executionId` that can be used with `ide_get_run_execution`, `ide_read_run_output`, and `ide_stop_run_execution`.

**Use when:**
- Starting a known run configuration without manual IDE interaction
- Triggering run/debug flows from the MCP client

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | string | No* | Stable configuration id returned by `ide_list_run_configurations` |
| `name` | string | No* | Exact run configuration name (use only when `id` is unavailable) |
| `executorId` | string | No | Executor id such as `Run` or `Debug` (default: `Run`) |
| `waitFor` | string | No | Wait target: `started`, `first_output`, or `completed` (default: `completed`) |
| `timeout` | integer | No | Maximum time to wait in milliseconds before returning (default: `20000`) |
| `maxLinesCount` | integer | No | Maximum number of output lines to return when truncation is enabled (default: `200`) |
| `truncateMode` | string | No | Output truncation mode: `start`, `middle`, `end`, or `none` (default: `start`, which keeps the latest lines) |

*Either `id` or `name` must be provided. `id` is recommended because names can be ambiguous.

**Example Request:**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_run_configuration",
    "arguments": {
      "id": "Application:Demo",
      "executorId": "Debug",
      "waitFor": "first_output",
      "timeout": 60000,
      "maxLinesCount": 150
    }
  }
}
```

**Example Response:**

```json
{
  "executionId": "123e4567-e89b-12d3-a456-426614174000",
  "id": "Application:Demo",
  "name": "Demo",
  "executorId": "Debug",
  "waitFor": "first_output",
  "waitOutcome": "first_output",
  "started": true,
  "completed": false,
  "timedOut": false,
  "success": null,
  "exitCode": null,
  "terminationReason": null,
  "output": "Application starting...\n",
  "outputLength": 24,
  "lastChunkLength": 24,
  "truncated": false,
  "timeoutMs": 60000,
  "message": "Run configuration 'Demo' produced output with executor 'Debug'."
}
```

### ide_get_run_execution

> **Availability**: Universal Tool - works in all JetBrains IDEs

Returns the current status of a tracked run execution.

**Use when:**
- A previous `ide_run_configuration` call timed out and you want to see whether it is still running
- You need the latest `outputOffset` before polling `ide_read_run_output`

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `executionId` | string | Yes | Execution id returned by `ide_run_configuration` |

**Example Response:**

```json
{
  "executionId": "123e4567-e89b-12d3-a456-426614174000",
  "id": "Application:Demo",
  "name": "Demo",
  "executorId": "Debug",
  "status": "running",
  "running": true,
  "completed": false,
  "stopRequested": false,
  "success": null,
  "exitCode": null,
  "terminationReason": null,
  "outputOffset": 248,
  "outputLength": 248,
  "lastChunkLength": 32,
  "startedAtMs": 1741600081000,
  "finishedAtMs": null,
  "message": null
}
```

### ide_read_run_output

> **Availability**: Universal Tool - works in all JetBrains IDEs

Reads output from a tracked run execution starting at a character offset.

**Use when:**
- You want incremental stdout/stderr after a timed-out or long-running execution
- You are polling output in a loop from the last `nextOffset`

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `executionId` | string | Yes | Execution id returned by `ide_run_configuration` |
| `since` | integer | No | Character offset to read from (default: `0`) |

**Example Response:**

```json
{
  "executionId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "running",
  "completed": false,
  "success": null,
  "exitCode": null,
  "terminationReason": null,
  "since": 248,
  "nextOffset": 321,
  "outputLength": 321,
  "lastChunkLength": 73,
  "output": "Compilation finished\nApplication window opened\n"
}
```

### ide_stop_run_execution

> **Availability**: Universal Tool - works in all JetBrains IDEs

Requests that a tracked run execution be stopped.

**Use when:**
- A long-running desktop/server process is no longer needed
- A run was started by an agent and should be shut down programmatically

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `executionId` | string | Yes | Execution id returned by `ide_run_configuration` |
| `waitUntilStopped` | boolean | No | Whether to wait for the process to stop before returning (default: `false`) |
| `timeout` | integer | No | Maximum wait in milliseconds when `waitUntilStopped=true` (default: `20000`) |

**Example Response:**

```json
{
  "executionId": "123e4567-e89b-12d3-a456-426614174000",
  "stopRequested": true,
  "wasRunning": true,
  "completed": true,
  "success": false,
  "exitCode": 143,
  "terminationReason": "stopped_by_user",
  "waitOutcome": "completed",
  "message": "Run execution '123e4567-e89b-12d3-a456-426614174000' has stopped."
}
```

---

## Refactoring Tools

> **Note**: All refactoring tools modify source files. Changes can be undone with Ctrl/Cmd+Z.

### ide_refactor_rename (Universal - All Languages)

Renames a symbol and updates all references across the project. This tool uses IntelliJ's `RenameProcessor` which is language-agnostic and works across **all languages** supported by your IDE.

**Supported Languages:** Java, Kotlin, Python, JavaScript, TypeScript, Go, PHP, Rust, Ruby, and any language with IntelliJ plugin support.

**Features:**
- Language-specific name validation (identifier rules, keyword detection)
- **Fully headless/autonomous operation** (no popups or dialogs)
- **Automatic related element renaming** - getters/setters, overriding methods, test classes are renamed automatically
- Conflict detection before rename execution (returns error instead of showing dialog)
- Single atomic operation - all renames (primary + related) can be undone with one Ctrl/Cmd+Z

**Use when:**
- Renaming identifiers to improve code clarity
- Following naming conventions
- Refactoring code structure

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file` | string | Yes | Path to the file containing the symbol |
| `line` | integer | Yes | 1-based line number |
| `column` | integer | Yes | 1-based column number |
| `newName` | string | Yes | The new name for the symbol |

**Example Request (Java):**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_refactor_rename",
    "arguments": {
      "file": "src/main/java/com/example/UserService.java",
      "line": 15,
      "column": 17,
      "newName": "findUserById"
    }
  }
}
```

**Example Request (Python):**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_refactor_rename",
    "arguments": {
      "file": "src/services/user_service.py",
      "line": 10,
      "column": 5,
      "newName": "fetch_user_data"
    }
  }
}
```

**Example Request (PHP):**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_refactor_rename",
    "arguments": {
      "file": "src/Models/User.php",
      "line": 25,
      "column": 21,
      "newName": "getFullName"
    }
  }
}
```

**Example Response:**

```json
{
  "success": true,
  "affectedFiles": [
    "src/main/java/com/example/UserService.java",
    "src/main/java/com/example/UserController.java",
    "src/test/java/com/example/UserServiceTest.java"
  ],
  "changesCount": 3,
  "message": "Successfully renamed 'findUser' to 'findUserById' (also renamed 2 related element(s))"
}
```

**Automatic Related Renames:**

Related elements are automatically renamed without any prompts or dialogs:

| Language | What Gets Auto-Renamed |
|----------|------------------------|
| Java/Kotlin | Getters/setters for fields, constructor parameters ↔ matching fields, overriding methods in subclasses, test classes |
| All Languages | Method implementations in subclasses, interface method implementations |

All renames happen in a single atomic operation, so one undo (Ctrl/Cmd+Z) reverts everything.

---

## Java-Specific Refactoring Tools

These tools require the Java plugin and are only available in **IntelliJ IDEA** and **Android Studio**.

### ide_refactor_safe_delete

Safely deletes an element, first checking for usages.

**Use when:**
- Removing unused code
- Cleaning up dead code
- Safely removing methods or classes

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file` | string | Yes | Path to the file |
| `line` | integer | Yes | 1-based line number |
| `column` | integer | Yes | 1-based column number |
| `force` | boolean | No | Force deletion even if usages exist (default: false) |

**Example Request:**

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_refactor_safe_delete",
    "arguments": {
      "file": "src/main/java/com/example/LegacyHelper.java",
      "line": 8,
      "column": 14
    }
  }
}
```

**Example Response (safe to delete):**

```json
{
  "success": true,
  "message": "Successfully deleted 'LegacyHelper'"
}
```

**Example Response (blocked by usages):**

```json
{
  "success": false,
  "message": "Cannot safely delete: 3 usages found",
  "blockingUsages": [
    {
      "file": "src/main/java/com/example/App.java",
      "line": 25,
      "context": "LegacyHelper.convert(data)"
    }
  ]
}
```

---

## Error Handling

### JSON-RPC Standard Errors

| Code | Name | When It Occurs |
|------|------|----------------|
| -32700 | Parse Error | Invalid JSON in request |
| -32600 | Invalid Request | Missing required JSON-RPC fields |
| -32601 | Method Not Found | Unknown tool or method name |
| -32602 | Invalid Params | Missing or invalid parameters |
| -32603 | Internal Error | Unexpected server error |

### Custom MCP Errors

| Code | Name | When It Occurs |
|------|------|----------------|
| -32001 | Index Not Ready | IDE is indexing (dumb mode) |
| -32002 | File Not Found | Specified file doesn't exist |
| -32003 | Symbol Not Found | No symbol at the specified position |
| -32004 | Refactoring Conflict | Refactoring cannot be completed |

### Example Error Response

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32001,
    "message": "IDE is in dumb mode, indexes not available. Please wait for indexing to complete."
  }
}
```

### Handling Dumb Mode

Before calling index-dependent tools, you can check the index status:

```json
{
  "method": "tools/call",
  "params": {
    "name": "ide_index_status",
    "arguments": {}
  }
}
```

If `isDumbMode` is `true`, wait and retry later.
