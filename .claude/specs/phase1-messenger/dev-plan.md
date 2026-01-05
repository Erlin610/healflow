# Phase 1: The Messenger - 开发计划

## 概述
打通异常上报链路，实现客户端通过 Starter 向 Platform 上报异常事件的完整流程。

## 任务拆解

### 任务 1: Common DTO 定义
- **ID**: task-1
- **描述**: 在 healflow-common 模块中定义异常上报数据传输对象，使用 JDK 21 record 特性
- **文件范围**: healflow-common/src/main/java/com/healflow/common/dto/
- **依赖关系**: None
- **验证命令**: mvn -pl healflow-common -DskipTests package
- **验证重点**:
  - IncidentReport record 包含所有必需字段（appId, commitId, errorType, errorMessage, stackTrace, environment, occurredAt）
  - 编译通过无错误
  - 字段类型正确（String, LocalDateTime 等）

### 任务 2: Platform 接收端实现
- **ID**: task-2
- **描述**: 在 healflow-platform 模块中实现异常上报接收端点，接收 IncidentReport 并记录日志
- **文件范围**: healflow-platform/src/main/java/com/healflow/platform/controller/
- **依赖关系**: depends on task-1
- **验证命令**: mvn -pl healflow-platform -am -DskipTests package
- **验证重点**:
  - IncidentController 正确注入依赖
  - POST /api/v1/incidents/report 端点可访问
  - 接收 JSON 请求体并反序列化为 IncidentReport
  - 日志输出包含关键字段信息
  - 返回 "RECEIVED" 响应

### 任务 3: Starter 发送端实现
- **ID**: task-3
- **描述**: 在 healflow-spring-boot-starter 模块中实现异常捕获、配置管理和自动上报功能
- **文件范围**:
  - healflow-spring-boot-starter/src/main/java/com/healflow/starter/config/
  - healflow-spring-boot-starter/src/main/java/com/healflow/starter/reporter/
  - healflow-spring-boot-starter/src/main/java/com/healflow/starter/handler/
  - healflow-spring-boot-starter/src/main/resources/META-INF/spring/
- **依赖关系**: depends on task-1
- **验证命令**: mvn -pl healflow-spring-boot-starter -am -DskipTests package
- **验证重点**:
  - HealFlowProperties 正确绑定配置前缀 healflow
  - IncidentReporter 使用 RestClient 发送 POST 请求
  - HealFlowExceptionHandler 捕获全局异常并调用 IncidentReporter
  - HealFlowAutoConfiguration 正确注册所有 Bean
  - META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 包含配置类全限定名
  - 编译通过且 Spring Boot 自动装配元数据生成正确

## 验收标准
- [ ] IncidentReport DTO 定义完整，包含 7 个必需字段
- [ ] Platform 提供 POST /api/v1/incidents/report 端点并能接收上报
- [ ] Starter 实现全局异常捕获和自动上报机制
- [ ] 所有模块编译通过（mvn -DskipTests package）
- [ ] AutoConfiguration.imports 配置正确，Spring Boot 能识别自动装配
- [ ] 跳过单元测试（Phase 1 仅验证编译和基础功能）

## 技术要点
- **JDK 21 特性**: 使用 record 定义不可变 DTO，简化代码
- **Spring Boot 3.3.6**: 使用 RestClient（替代已废弃的 RestTemplate）进行 HTTP 调用
- **自动装配机制**: 通过 AutoConfiguration.imports 实现 Starter 零配置集成
- **依赖管理**: task-1 必须先完成，task-2 和 task-3 可并行开发
- **验证策略**: Phase 1 专注于链路打通，暂不编写单元测试，仅验证编译通过
- **配置约定**: 使用 `healflow.platform.url` 等配置项，遵循 Spring Boot 配置规范
