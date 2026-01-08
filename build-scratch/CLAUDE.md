# CLAUDE.md

## Project Overview

**HealFlow** is an AIOps Self-Healing Engine for Spring Boot applications. It transforms runtime exceptions into actionable merge requests by using AI agents (like Claude Code) running in isolated Docker containers to diagnose and fix code issues automatically.

**Key Concept**: "Turn Runtime Exceptions into Merge Requests."

### Core Features
- Exception capture and automatic incident reporting
- Source code synchronization via JGit (fetch/clone by commit ID)
- Docker sandbox execution with ProcessBuilder IO interception
- Interactive AI agent control with auto-approval policies
- Patch generation and code diff export

---

## Architecture

### Host-Container Hybrid Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Host Machine                              │
│  ┌─────────────────┐    ┌─────────────────────────────────────┐ │
│  │ healflow-platform │──▶│  healflow-engine                    │ │
│  │   (Spring Boot)   │    │  ┌───────────┐  ┌──────────────┐  │ │
│  │  - REST API      │    │  │ JGit      │  │ Workspace    │  │ │
│  │  - Incident Mgmt │    │  │ Manager   │  │ Service      │  │ │
│  └─────────────────┘    │  └───────────┘  └──────────────┘  │ │
│                         └─────────────────────────────────────┘ │
│                         /data/workspace/{appName}/              │
└─────────────────────────────────────────────────────────────────┘
                                 │
                    Volume Mount │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Docker Container (Sandbox)                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  AI Agent (Claude Code)                                    │  │
│  │  - Reads mounted source code                               │  │
│  │  - Analyzes stacktrace                                     │  │
│  │  - Proposes fixes                                          │  │
│  └───────────────────────────────────────────────────────────┘  │
│  /src  ← mounted from host                                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## Key Modules

### 1. `healflow-common` (Shared Utilities)
- **DTOs**: `IncidentReport`, `PatchProposal`, `AnalysisResult`, `FixProposal`, `FixResult`
- **Enums**: `AgentStatus` (ANALYZING, WAITING_AUTH, DONE)
- **Location**: `healflow-common/src/main/java/com/healflow/common/`

### 2. `healflow-engine` (Core Engine)
- **Git Operations**: `JGitManager`, `GitWorkspaceManager` - Clone, fetch, reset by commit ID
- **Sandbox**: `DockerSandboxManager` - Docker container lifecycle management
- **Shell**: `InteractiveShellRunner`, `ShellRunner` - ProcessBuilder-based command execution with IO interception
- **Workspace**: `WorkspaceService` - Manages project workspace on host
- **Location**: `healflow-engine/src/main/java/com/healflow/engine/`

### 3. `healflow-spring-boot-starter` (Client SDK)
- **Exception Capture**: `ExceptionListener` - Global exception handler
- **Reporter**: `IncidentReporter` - Sends incidents to Platform
- **Config**: `HealFlowAutoConfiguration` - Spring Boot auto-setup
- **Location**: `healflow-spring-boot-starter/src/main/java/com/healflow/starter/`

### 4. `healflow-platform` (Server Application)
- **REST API**: `IncidentController`, `ReportController` - Endpoints for incident submission
- **Services**: `HealingService`, `IncidentService` - Business logic orchestration
- **Config**: `EngineConfiguration` - Engine bean wiring
- **Location**: `healflow-platform/src/main/java/com/healflow/platform/`

### 5. `healflow-demo` (Demo Application)
- Sample Spring Boot app to demonstrate exception triggering
- **Location**: `healflow-demo/src/main/java/com/healflow/demo/`

---

## Build Instructions

### Prerequisites
- **Java**: JDK 21+
- **Maven**: 3.9+
- **Docker**: Required for sandbox execution
- **Git**: Required for JGit operations

### Build Commands

```bash
# Full build with tests
mvn clean compile

# Run tests with coverage
mvn clean test jacoco:report

# Run full verification (includes coverage checks - 90% minimum)
mvn clean verify

# Build specific module
mvn -pl healflow-platform clean package

# Skip coverage enforcement for quick builds
mvn clean verify -Djacoco.skip=true
```

### IDE Setup
- Project uses Lombok - ensure annotation processing is enabled
- Maven wrapper: Use `.m2/` directory for local repository
- Code style: Follow existing patterns in source files

---

## Development Guidelines

### Code Style
- **Package structure**: `com.healflow.{module}.{component}`
- **Interface-based design**: Core interfaces in root package (e.g., `HealflowEngine`)
- **Async processing**: Use `@EnableAsync` + `@Async` for long-running tasks
- **StreamGobbler**: Always use for consuming process output (prevents deadlocks)

### Testing
- Unit tests alongside source files in `src/test/java/`
- Integration tests in `src/test/java/.../integration/`
- Coverage requirement: **90% line coverage minimum**
- Mock agent available: `mock-agent.sh` for local testing

### Common Patterns

#### Process Execution with IO Handling
```java
// Use ShellRunner for interactive commands
ShellCommand cmd = new ShellCommand("docker exec", containerId, "claude", "analyze");
CommandResult result = shellRunner.runInteractive(cmd, rules);
```

#### Incident Flow
1. Exception caught by `ExceptionListener` → `IncidentReporter`
2. Platform receives via `IncidentController` → `IncidentService`
3. `HealingService` orchestrates `HealflowEngine`
4. Engine uses `GitWorkspaceManager` + `DockerSandboxManager`
5. `InteractiveShellRunner` controls AI agent with auto-approval rules
6. Patch generated via `git diff` → returned to caller

### Configuration
- Platform config: `healflow-platform/src/main/resources/application.yml`
- Engine config: `healflow-engine/src/main/resources/`
- Sandbox images: Configured via properties, inject API keys via environment

---

## Working with the Project

### Quick Start for Development
```bash
# 1. Build all modules
mvn clean compile -DskipTests

# 2. Run platform
cd healflow-platform && mvn spring-boot:run

# 3. Run demo (in another terminal)
cd healflow-demo && mvn spring-boot:run

# 4. Trigger test exception
curl http://localhost:8081/demo/trigger-error
```

### Debugging Tips
- Check `StreamGobbler` logs for process IO issues
- Use `docker logs <container>` for sandbox debugging
- Review `InteractionRule` patterns for auto-approval logic
- Enable DEBUG logging for detailed execution traces

---

## File Locations

| Path | Purpose |
|------|---------|
| `pom.xml` | Parent POM, manages versions and plugins |
| `healflow-engine/src/main/java/com/healflow/engine/` | Core engine classes |
| `healflow-platform/src/main/java/com/healflow/platform/` | REST API and services |
| `healflow-spring-boot-starter/src/main/java/com/healflow/starter/` | Client SDK |
| `healflow-common/src/main/java/com/healflow/common/` | Shared DTOs and enums |
| `healflow-engine/src/main/resources/docker/` | Docker-related resources |
| `doc/架构.md` | Architecture documentation (Chinese) |
