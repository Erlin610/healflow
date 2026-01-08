# Phase 5: AI Core Integration - Development Plan

## Overview
Integrate AI-powered analysis and fix generation capabilities into the incident workflow, including workspace management, API key resolution, and comprehensive testing coverage.

## Task Breakdown

### Task 1: Fix workflow state + workspace correctness
- **ID**: task-1
- **Description**: Refactor IncidentController to use GitWorkspaceManager for proper workspace preparation and store real Path objects instead of placeholder strings. Ensure workspace state is correctly initialized before AI operations.
- **File Scope**:
  - `healflow-platform/src/main/java/com/healflow/platform/controller/IncidentController.java`
  - `healflow-engine/src/main/java/com/healflow/engine/git/GitWorkspaceManager.java`
- **Dependencies**: None
- **Test Command**: `mvn clean test jacoco:report -pl healflow-platform,healflow-engine`
- **Test Focus**:
  - Workspace directory creation and initialization
  - Path object storage in workflow state
  - GitWorkspaceManager integration correctness
  - Error handling for workspace preparation failures

### Task 2: Harden IncidentService stage APIs + API key resolution
- **ID**: task-2
- **Description**: Improve IncidentService stage methods (analyze, propose, apply) to correctly read API keys from real settings.json format. Add robust error handling for missing or invalid API keys and enhance stage transition logic.
- **File Scope**:
  - `healflow-platform/src/main/java/com/healflow/platform/service/IncidentService.java`
- **Dependencies**: depends on task-1
- **Test Command**: `mvn clean test jacoco:report -pl healflow-platform`
- **Test Focus**:
  - API key resolution from settings.json
  - Stage method error handling (missing keys, invalid format)
  - Stage transition correctness
  - Integration with workspace paths from Task 1

### Task 3: Add healflow-common unit tests for Phase-5 DTOs
- **ID**: task-3
- **Description**: Create comprehensive unit tests for Phase 5 DTOs (AnalysisResult, FixProposal, FixResult) to ensure module-local JaCoCo coverage requirements are met. Test serialization, validation, and edge cases.
- **File Scope**:
  - `healflow-common/src/main/java/com/healflow/common/dto/AnalysisResult.java`
  - `healflow-common/src/main/java/com/healflow/common/dto/FixProposal.java`
  - `healflow-common/src/main/java/com/healflow/common/dto/FixResult.java`
  - `healflow-common/src/test/java/com/healflow/common/dto/AnalysisResultTest.java` (new)
  - `healflow-common/src/test/java/com/healflow/common/dto/FixProposalTest.java` (new)
  - `healflow-common/src/test/java/com/healflow/common/dto/FixResultTest.java` (new)
- **Dependencies**: None
- **Test Command**: `mvn clean test jacoco:report -pl healflow-common`
- **Test Focus**:
  - DTO field validation and constraints
  - JSON serialization/deserialization
  - Builder pattern correctness
  - Null handling and edge cases
  - Equals/hashCode/toString methods

### Task 4: Add platform tests for Phase-5 endpoints + service stages
- **ID**: task-4
- **Description**: Extend IncidentControllerTest and IncidentServiceTest to cover Phase 5 endpoints (analyze, propose, apply) and service stage methods. Mock AI service interactions and verify workflow state transitions.
- **File Scope**:
  - `healflow-platform/src/test/java/com/healflow/platform/controller/IncidentControllerTest.java`
  - `healflow-platform/src/test/java/com/healflow/platform/service/IncidentServiceTest.java`
- **Dependencies**: depends on task-1, task-2
- **Test Command**: `mvn clean test jacoco:report -pl healflow-platform`
- **Test Focus**:
  - POST /api/incidents/{id}/analyze endpoint
  - POST /api/incidents/{id}/propose endpoint
  - POST /api/incidents/{id}/apply endpoint
  - Service stage method coverage with mocked dependencies
  - Error response handling (missing API keys, workspace failures)
  - Workflow state validation across stages

## Acceptance Criteria
- [ ] IncidentController uses GitWorkspaceManager for workspace preparation
- [ ] Real Path objects stored in workflow state
- [ ] IncidentService correctly resolves API keys from settings.json
- [ ] All Phase 5 DTOs have comprehensive unit tests
- [ ] All Phase 5 endpoints have integration tests
- [ ] All unit tests pass: `mvn clean test`
- [ ] Code coverage ≥90% for all modified modules
- [ ] JaCoCo report shows no coverage regressions

## Technical Notes
- **Workspace Management**: GitWorkspaceManager should handle directory creation, git initialization, and cleanup. Controller delegates all workspace operations to this manager.
- **API Key Resolution**: Settings.json format may vary; implement defensive parsing with clear error messages for missing/invalid keys.
- **Test Isolation**: Use @TempDir for workspace tests, mock AI service calls to avoid external dependencies.
- **Coverage Strategy**: Task 3 runs independently to protect healflow-common module coverage; Task 4 depends on implementation tasks to test integrated behavior.
- **Maven Multi-Module**: Use `-pl` flag to run tests for specific modules during development; final verification runs full `mvn clean test jacoco:report` at root.
