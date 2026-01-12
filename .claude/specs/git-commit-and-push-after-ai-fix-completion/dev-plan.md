# Git Commit and Push After AI Fix Completion - Development Plan

## Overview
在 AI 修复完成后自动提交代码变更并推送到远程仓库，使用固定格式的提交信息。

## Task Breakdown

### Task 1: 扩展 JGitManager API
- **ID**: task-1
- **Type**: default
- **Description**: 在 JGitManager 中添加 commitFix 和 push 方法，支持清理日志文件、检测无变更场景、处理推送失败
- **File Scope**: healflow-engine/src/main/java/com/healflow/engine/git/JGitManager.java, healflow-engine/src/test/java/com/healflow/engine/git/JGitManagerSandboxTest.java
- **Dependencies**: None
- **Test Command**: mvn test -Dtest=JGitManagerSandboxTest --projects healflow-engine -Djacoco.destFile=target/jacoco.exec
- **Test Focus**:
  - 提交信息格式正确性（"fix: [incident-id] [error-type]"）
  - 清理日志文件后再提交
  - 无变更时的处理逻辑
  - 推送失败时的异常处理
  - 推送到指定分支的正确性

### Task 2: 集成到 IncidentService
- **ID**: task-2
- **Type**: default
- **Description**: 在 IncidentService.startFixWithAnswers 方法中调用 JGitManager 的 commitFix 和 push，从 incident 记录中获取分支信息
- **File Scope**: healflow-platform/src/main/java/com/healflow/platform/service/IncidentService.java
- **Dependencies**: depends on task-1
- **Test Command**: mvn test -Dtest=IncidentServiceTest --projects healflow-platform -Djacoco.destFile=target/jacoco.exec
- **Test Focus**:
  - 修复成功后自动触发提交和推送
  - 使用 incident 记录中的分支信息
  - 提交失败时不影响修复流程的完成状态
  - 推送失败时的错误日志记录

### Task 3: 添加集成测试
- **ID**: task-3
- **Type**: quick-fix
- **Description**: 在 IncidentServiceTest 中添加端到端集成测试，验证完整的修复-提交-推送流程
- **File Scope**: healflow-platform/src/test/java/com/healflow/platform/service/IncidentServiceTest.java
- **Dependencies**: depends on task-2
- **Test Command**: mvn test -Dtest=IncidentServiceTest#testFixWithCommitAndPush --projects healflow-platform -Djacoco.destFile=target/jacoco.exec
- **Test Focus**:
  - 完整流程：异常修复 → 代码变更 → 提交 → 推送
  - Mock Git 远程仓库交互
  - 验证提交信息格式和内容
  - 验证推送到正确的分支

## Acceptance Criteria
- [ ] JGitManager 支持 commitFix 和 push 方法
- [ ] 提交信息格式为 "fix: [incident-id] [error-type]"
- [ ] 提交前自动清理日志文件（*.log, *.tmp）
- [ ] 正确处理无变更场景（不创建空提交）
- [ ] 推送到 incident 记录中指定的分支
- [ ] 推送失败时记录错误但不阻断修复流程
- [ ] 所有单元测试通过
- [ ] 代码覆盖率 ≥90%

## Technical Notes
- 使用 JGit API 的 `git.add()`, `git.commit()`, `git.push()` 方法
- 清理日志文件使用 `git.rm()` 或文件系统删除后 `git.add(".")`
- 无变更检测：检查 `git.status().call().isClean()`
- 推送失败可能原因：网络问题、权限不足、分支保护规则
- 提交和推送操作应该是可选的（通过配置控制），但默认启用
- 考虑添加重试机制处理临时网络故障
- 错误类型从 IncidentEntity 的 errorType 字段获取
