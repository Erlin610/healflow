# Git Commit Information Storage - Development Plan

## Overview
扩展 IncidentEntity 以存储 Git 提交元数据（commitId、commitMessage、changedFiles、gitDiff），并修改 JGitManager 和 IncidentService 以捕获和持久化这些信息。

## Task Breakdown

### Task 1: Extend JGitManager + Add DTOs
- **ID**: task-1
- **Type**: default
- **Description**: 在 engine 模块创建 CommitInfo 和 FileDiffStat DTO，修改 JGitManager.commitFix() 返回 Optional<CommitInfo>，包含 commitId、commitMessage、changedFiles 列表和 gitDiff 统计信息（每个文件的增删行数）
- **File Scope**: healflow-engine/src/main/java/com/healflow/engine/git/JGitManager.java, healflow-engine/src/main/java/com/healflow/engine/dto/CommitInfo.java, healflow-engine/src/main/java/com/healflow/engine/dto/FileDiffStat.java, healflow-engine/src/test/java/com/healflow/engine/git/
- **Dependencies**: None
- **Test Command**: mvn -pl healflow-engine clean test -Dtest=JGitManagerTest -Djacoco.skip=true
- **Test Focus**: 验证 commitFix() 返回完整的 CommitInfo 对象，包含正确的文件路径列表和每个文件的增删行统计；测试空提交场景返回 Optional.empty()

### Task 2: Add Fields to IncidentEntity
- **ID**: task-2
- **Type**: quick-fix
- **Description**: 在 IncidentEntity 添加四个字段：commitId (String)、commitMessage (String)、changedFiles (String, 存储 JSON 数组)、gitDiff (String, 存储 JSON 对象数组)，添加对应的 getter/setter
- **File Scope**: healflow-platform/src/main/java/com/healflow/platform/entity/IncidentEntity.java
- **Dependencies**: None
- **Test Command**: mvn -pl healflow-platform clean compile -DskipTests
- **Test Focus**: 编译验证字段定义正确，无语法错误

### Task 3: Update IncidentService to Store Commit Info
- **ID**: task-3
- **Type**: default
- **Description**: 修改 IncidentService.commitAndPushFixIfPossible() 方法，调用 JGitManager.commitFix() 获取 CommitInfo，将 changedFiles 序列化为 JSON 数组字符串，将 gitDiff 序列化为包含 fileName/addedLines/deletedLines 的 JSON 对象数组，更新 IncidentEntity 并保存
- **File Scope**: healflow-platform/src/main/java/com/healflow/platform/service/IncidentService.java, healflow-platform/src/test/java/com/healflow/platform/service/
- **Dependencies**: depends on task-1, depends on task-2
- **Test Command**: mvn -pl healflow-platform clean test -Dtest=IncidentServiceTest -Djacoco.skip=true
- **Test Focus**: 验证成功提交后 IncidentEntity 的四个字段正确填充；验证 JSON 序列化格式符合预期；测试提交失败时字段保持为 null

## Acceptance Criteria
- [ ] JGitManager.commitFix() 返回包含完整元数据的 Optional<CommitInfo>
- [ ] IncidentEntity 包含 commitId、commitMessage、changedFiles (JSON)、gitDiff (JSON) 四个字段
- [ ] IncidentService 正确序列化并存储提交信息到数据库
- [ ] changedFiles 格式为 JSON 字符串数组：["path/to/file1.java", "path/to/file2.java"]
- [ ] gitDiff 格式为 JSON 对象数组：[{"fileName":"...", "addedLines":10, "deletedLines":5}]
- [ ] 所有单元测试通过
- [ ] 代码覆盖率 ≥90%

## Technical Notes
- 使用 JGit DiffFormatter 计算文件级别的增删行统计
- JSON 序列化使用 Jackson ObjectMapper（Spring Boot 默认提供）
- IncidentEntity 新增字段使用 @Column(columnDefinition = "TEXT") 以支持大型 diff
- CommitInfo 和 FileDiffStat 作为纯数据传输对象，无需持久化注解
- 保持向后兼容：新字段允许为 null，不影响现有功能
