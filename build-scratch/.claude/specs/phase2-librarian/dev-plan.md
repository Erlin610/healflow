# Phase 2: The Librarian - 开发计划

## 概述
实现 Git 代码同步机制，使 Platform 能够根据 repoUrl 和 commitId 还原事故现场源码。

## 任务拆解

### Task 1: Common DTO 扩展
- **ID**: task-1
- **描述**: 扩展 IncidentReport DTO，添加 repoUrl 字段以支持 Git 仓库信息传递
- **文件范围**: healflow-common/src/main/java/com/healflow/common/dto/**, healflow-common/src/test/java/com/healflow/common/dto/**
- **依赖**: None
- **测试命令**: `mvn test -pl healflow-common -Dtest=IncidentReportTest --fail-at-end`
- **测试重点**:
  - repoUrl 字段序列化/反序列化
  - 空值和非法 URL 处理
  - 向后兼容性（无 repoUrl 的旧数据）

### Task 2: Engine Git 工作区管理器
- **ID**: task-2
- **描述**: 重构 JGitManager 为 GitWorkspaceManager，实现 Spring Bean 化管理和完整的 Git 操作（clone、update、checkout）
- **文件范围**: healflow-engine/src/main/java/com/healflow/engine/git/**, healflow-engine/src/test/java/com/healflow/engine/git/**
- **依赖**: None
- **测试命令**: `mvn test -pl healflow-engine -Dtest=GitWorkspaceManagerTest --fail-at-end`
- **测试重点**:
  - prepareWorkspace 首次 clone 和增量 update 逻辑
  - checkoutCommit 切换到指定 commitId
  - 并发安全性（多线程访问同一仓库）
  - 异常场景（网络失败、无效 commitId、磁盘空间不足）

### Task 3: Platform 异步事故处理
- **ID**: task-3
- **描述**: 创建 IncidentService 实现异步事故处理，集成 GitWorkspaceManager 还原源码，更新 Controller 调用链
- **文件范围**: healflow-platform/src/main/java/com/healflow/platform/**, healflow-platform/src/test/java/com/healflow/platform/**
- **依赖**: depends on task-1, task-2
- **测试命令**: `mvn test -pl healflow-platform -Dtest=IncidentServiceTest,IncidentControllerTest --fail-at-end`
- **测试重点**:
  - 异步方法执行验证（@Async 生效）
  - GitWorkspaceManager 调用正确性
  - Controller 返回 202 Accepted 状态码
  - 组件扫描覆盖 com.healflow 包

### Task 4: Starter repoUrl 透传
- **ID**: task-4
- **描述**: 扩展 HealFlowProperties 添加 gitUrl 配置，IncidentReporter 自动注入并传递到 IncidentReport
- **文件范围**: healflow-spring-boot-starter/src/main/java/com/healflow/starter/**, healflow-spring-boot-starter/src/test/java/com/healflow/starter/**, demo/src/main/resources/application.yml
- **依赖**: depends on task-1
- **测试命令**: `mvn test -pl healflow-spring-boot-starter -Dtest=IncidentReporterTest --fail-at-end`
- **测试重点**:
  - gitUrl 配置注入验证
  - IncidentReporter 正确传递 repoUrl
  - 配置缺失时的降级行为（repoUrl 为 null）
  - Demo 应用集成测试

## 验收标准
- [ ] IncidentReport 包含 repoUrl 字段且向后兼容
- [ ] GitWorkspaceManager 能够 clone、update、checkout 指定 commitId
- [ ] Platform 异步处理事故并调用 GitWorkspaceManager
- [ ] Starter 自动注入 gitUrl 并透传到 Platform
- [ ] 使用 https://github.com/Erlin610/healflow.git 完成端到端测试
- [ ] 所有单元测试通过
- [ ] 代码覆盖率 ≥90%

## 技术要点
- **并发安全**: GitWorkspaceManager 使用 ConcurrentHashMap 管理多仓库工作区，避免重复 clone
- **异步配置**: Platform 需开启 @EnableAsync 并配置线程池（建议 core=4, max=8）
- **错误处理**: Git 操作失败时记录日志但不阻塞主流程，Platform 返回 202 后异步失败需有补偿机制
- **测试隔离**: 单元测试使用临时目录（java.io.tmpdir），测试后清理避免磁盘占用
- **配置约定**: healflow.git-url 为可选配置，未配置时 repoUrl 为 null 但不影响其他功能
