# Phase 4: The Puppet Master (Interactive Control) - Development Plan

## Overview
实现交互式沙箱控制能力，通过 InteractiveRunner 适配器自动响应 AI 代理的交互式提示，支持 docker run -i 一次性交互执行。

## Task Breakdown

### Task 1: Fix mock-agent.sh Script
- **ID**: task-1
- **Description**: 修复 mock-agent.sh 脚本的语法错误（缺失 fi、多余 end），确保脚本可正常执行并模拟 AI 交互提示
- **File Scope**: healflow-demo/mock-agent.sh
- **Dependencies**: None
- **Test Command**: `bash healflow-demo/mock-agent.sh < /dev/null` (验证脚本语法正确且可执行)
- **Test Focus**:
  - 脚本语法检查通过（bash -n）
  - 模拟交互提示正确输出
  - 读取标准输入并响应

### Task 2: Implement InteractiveRunner Adapter
- **ID**: task-2
- **Description**: 创建 InteractiveRunner 作为 InteractionRule 的适配器，封装自动批准策略（如自动回复 "yes"），复用 InteractiveShellRunner 的交互能力
- **File Scope**: healflow-engine/src/main/java/com/healflow/engine/sandbox/InteractiveRunner.java, healflow-engine/src/test/java/com/healflow/engine/sandbox/InteractiveRunnerTest.java
- **Dependencies**: None
- **Test Command**: `mvn clean test -pl healflow-engine -Dtest=InteractiveRunnerTest jacoco:report`
- **Test Focus**:
  - InteractionRule 策略正确应用（匹配提示并返回响应）
  - 与 InteractiveShellRunner 集成正确
  - 多轮交互场景（连续提示）
  - 超时和异常处理

### Task 3: Extend DockerSandboxManager for Interactive Runs
- **ID**: task-3
- **Description**: 扩展 DockerSandboxManager 支持 docker run -i 一次性交互执行，集成 InteractiveRunner 处理容器内交互提示
- **File Scope**: healflow-engine/src/main/java/com/healflow/engine/sandbox/DockerSandboxManager.java, healflow-engine/src/test/java/com/healflow/engine/sandbox/DockerSandboxManagerTest.java
- **Dependencies**: depends on task-2
- **Test Command**: `mvn clean test -pl healflow-engine -Dtest=DockerSandboxManagerTest jacoco:report`
- **Test Focus**:
  - docker run -i 命令正确构造（-i 标志、挂载路径、工作目录）
  - InteractiveRunner 正确注入和调用
  - 交互式输入/输出流正确连接
  - 容器清理保证（即使交互失败）
  - 与现有 docker exec -i 功能共存

### Task 4: Integrate into IncidentService
- **ID**: task-4
- **Description**: 在 IncidentService 中集成 InteractiveRunner，调用 DockerSandboxManager 执行 mock-agent.sh 并自动响应交互提示
- **File Scope**: healflow-platform/src/main/java/com/healflow/platform/service/IncidentService.java, healflow-platform/src/test/java/com/healflow/platform/service/IncidentServiceTest.java
- **Dependencies**: depends on task-2, task-3
- **Test Command**: `mvn clean test -pl healflow-platform -Dtest=IncidentServiceTest jacoco:report`
- **Test Focus**:
  - mock-agent.sh 在沙箱中成功执行
  - 交互提示被自动响应（无需人工干预）
  - 执行输出完整捕获
  - 异常场景：脚本不存在、交互超时、容器启动失败

### Task 5: Tests and Coverage Lock-in
- **ID**: task-5
- **Description**: 补充所有新增和修改代码的单元测试，确保覆盖率 ≥90%，重点覆盖交互策略和异常路径
- **File Scope**: healflow-engine/src/test/java/com/healflow/engine/sandbox/**, healflow-platform/src/test/java/com/healflow/platform/service/**
- **Dependencies**: depends on task-4
- **Test Command**: `mvn clean test jacoco:report && mvn jacoco:report-aggregate`
- **Test Focus**:
  - InteractiveRunner 的所有公共方法和策略分支
  - DockerSandboxManager 的交互式执行路径
  - IncidentService 的 mock-agent.sh 集成路径
  - Mock 交互场景（模拟提示和响应）
  - 异常场景：交互超时、策略不匹配、流关闭异常
  - 验证 target/site/jacoco/index.html 显示 ≥90% 覆盖率

## Acceptance Criteria
- [ ] mock-agent.sh 脚本语法正确且可执行
- [ ] InteractiveRunner 成功封装 InteractionRule 策略
- [ ] DockerSandboxManager 支持 docker run -i 交互式执行
- [ ] IncidentService 成功执行 mock-agent.sh 并自动响应交互提示
- [ ] 所有单元测试通过
- [ ] 代码覆盖率 ≥90%（通过 JaCoCo 报告验证）
- [ ] 交互式执行与现有功能向后兼容

## Technical Notes
- **复用现有组件**: InteractiveShellRunner 和 InteractionRule 已存在，InteractiveRunner 仅作为薄适配层
- **交互策略**: 初始实现使用简单的自动批准策略（如匹配 ".*\\?" 返回 "yes"），后续可扩展为可配置规则
- **流管理**: docker run -i 需要正确连接 stdin/stdout/stderr，复用 StreamGobbler 处理输出
- **超时控制**: 交互式执行需要合理的超时设置，避免无限等待
- **测试隔离**: 单元测试使用 Mock 模拟交互场景，不依赖真实 Docker 环境
- **向后兼容**: Phase 1/2/3 功能不受影响，交互式执行为新增能力
- **脚本路径**: mock-agent.sh 位于 healflow-demo 模块，需要正确挂载到容器中
