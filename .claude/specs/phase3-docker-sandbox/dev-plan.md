# Phase 3: The Jailer (Docker Sandbox Execution) - Development Plan

## Overview
集成 Docker 沙箱执行能力，在隔离容器中运行代码验证命令，完成从事件上报到代码准备再到沙箱执行的完整链路。

## Task Breakdown

### Task 1: Wire Sandbox Beans
- **ID**: task-1
- **Description**: 将 DockerSandboxManager 和 ShellRunner 注册为 Spring Bean，确保依赖注入可用
- **File Scope**: healflow-engine/src/main/java/com/healflow/engine/sandbox/**, healflow-engine/src/main/java/com/healflow/engine/config/** (如需新建配置类)
- **Dependencies**: None
- **Test Command**: `mvn clean test -pl healflow-engine -Dtest=*Config*Test,*Sandbox*Test jacoco:report`
- **Test Focus**:
  - Bean 注册成功且可注入
  - DockerSandboxManager 初始化参数正确（镜像名、挂载路径）
  - ShellRunner 可正常执行简单命令

### Task 2: Integrate Sandbox Execution in IncidentService
- **ID**: task-2
- **Description**: 在 IncidentService.handleIncident() 的 TODO 位置（第 31 行）集成沙箱执行，调用 DockerSandboxManager 执行 "ls -al /src" 验证代码挂载
- **File Scope**: healflow-platform/src/main/java/com/healflow/platform/service/IncidentService.java
- **Dependencies**: depends on task-1
- **Test Command**: `mvn clean test -pl healflow-platform -Dtest=IncidentServiceTest jacoco:report`
- **Test Focus**:
  - Git 准备完成后正确启动容器
  - "ls -al /src" 命令执行成功并返回输出
  - 容器生命周期管理（启动、执行、清理）
  - 异常场景：Docker 不可用、容器启动失败、命令执行超时

### Task 3: Enhance DockerSandboxManager API
- **ID**: task-3
- **Description**: 优化 DockerSandboxManager 的 API 设计，提供 Phase 3 友好的入口方法（如 executeInSandbox），封装 startDetached/exec/removeForce 的完整生命周期
- **File Scope**: healflow-engine/src/main/java/com/healflow/engine/sandbox/DockerSandboxManager.java
- **Dependencies**: depends on task-1
- **Test Command**: `mvn clean test -pl healflow-engine -Dtest=DockerSandboxManagerTest jacoco:report`
- **Test Focus**:
  - 单次执行方法正确封装生命周期
  - 挂载路径正确传递到容器
  - 命令输出完整捕获（stdout/stderr）
  - 资源清理保证（即使执行失败也移除容器）

### Task 4: Tests and Coverage Lock-in
- **ID**: task-4
- **Description**: 补充所有新增和修改代码的单元测试，确保覆盖率 ≥90%，重点覆盖异常路径和边界条件
- **File Scope**: healflow-engine/src/test/java/com/healflow/engine/sandbox/**, healflow-platform/src/test/java/com/healflow/platform/service/**
- **Dependencies**: depends on task-2, task-3
- **Test Command**: `mvn clean test jacoco:report && mvn jacoco:report-aggregate`
- **Test Focus**:
  - DockerSandboxManager 的所有公共方法
  - IncidentService 的沙箱集成路径
  - Mock Docker 客户端进行隔离测试
  - 异常场景：网络超时、容器 OOM、命令返回非零退出码
  - 验证 target/site/jacoco/index.html 显示 ≥90% 覆盖率

## Acceptance Criteria
- [ ] DockerSandboxManager 和 ShellRunner 已注册为 Spring Bean
- [ ] IncidentService 在 Git 准备后成功执行 "ls -al /src" 并记录输出
- [ ] 容器使用 ubuntu:latest 镜像且正确挂载工作目录
- [ ] 容器执行完成后自动清理（无残留容器）
- [ ] 所有单元测试通过
- [ ] 代码覆盖率 ≥90%（通过 JaCoCo 报告验证）
- [ ] 异常场景有明确的错误处理和日志记录

## Technical Notes
- **Docker 依赖**: 运行时需要 Docker daemon 可访问，测试中使用 Mock 或 Testcontainers
- **镜像配置**: application.yml 中已配置 `sandbox.image: ubuntu:latest`，无需硬编码
- **生命周期管理**: 必须保证容器清理，避免资源泄漏（使用 try-finally 或 @PreDestroy）
- **输出捕获**: StreamGobbler 已实现异步输出捕获，复用现有实现
- **测试隔离**: 单元测试不依赖真实 Docker 环境，使用 Mock；集成测试可选使用 Testcontainers
- **向后兼容**: Phase 1/2 功能不受影响，沙箱执行为新增能力
