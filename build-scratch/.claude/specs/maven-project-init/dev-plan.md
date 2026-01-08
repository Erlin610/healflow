# Maven 多模块项目初始化 - 开发计划

## 概述
构建基于 JDK 21 和 Spring Boot 3.3.x 的 Maven Reactor 多模块项目，包含通用层、引擎层、自动配置层和平台层四个模块。

## 任务拆解

### Task 1: Maven Reactor 与版本门禁
- **ID**: task-1
- **Description**: 创建父 POM（healflow-parent）和 4 个子模块的 POM 文件及目录骨架，配置统一的依赖管理（Spring Boot 3.3.x, JDK 21）、插件版本、编译参数（--enable-preview）和 Maven Enforcer 规则
- **File Scope**:
  - `pom.xml` (根目录父 POM)
  - `healflow-common/pom.xml`
  - `healflow-engine/pom.xml`
  - `healflow-spring-boot-starter/pom.xml`
  - `healflow-platform/pom.xml`
  - 各模块的 `src/main/java`, `src/main/resources`, `src/test/java` 目录结构
- **Dependencies**: None
- **Test Command**: `mvn clean verify -Denforcer.skip=false`
- **Test Focus**:
  - Maven Reactor 构建顺序正确
  - 版本门禁规则生效（JDK 21, Maven 3.9+）
  - 所有模块编译通过
  - 依赖传递关系正确（common → engine/starter → platform）

### Task 2: healflow-common DTO/enum 定义
- **ID**: task-2
- **Description**: 在 healflow-common 模块中实现核心数据传输对象和枚举类型，包括 IncidentReportDto（异常报告）、PatchProposalDto（补丁提案）、AgentStatus（代理状态枚举）及相关验证注解
- **File Scope**:
  - `healflow-common/src/main/java/com/healflow/common/dto/**`
  - `healflow-common/src/main/java/com/healflow/common/enums/**`
  - `healflow-common/src/test/java/com/healflow/common/dto/**`
  - `healflow-common/src/test/java/com/healflow/common/enums/**`
- **Dependencies**: depends on task-1
- **Test Command**: `mvn clean test -pl healflow-common -Dtest=*Dto*,*Enum* --fail-at-end -Djacoco.skip=false && mvn jacoco:report -pl healflow-common`
- **Test Focus**:
  - DTO 字段验证（@NotNull, @NotBlank 等）
  - 枚举值序列化/反序列化
  - JSON 转换正确性
  - Builder 模式功能
  - 覆盖率 ≥90%

### Task 3: healflow-engine Git + Sandbox 框架
- **ID**: task-3
- **Description**: 在 healflow-engine 模块中实现 Git 操作管理器（JGitManager）、工作空间服务（WorkspaceService）、Docker 沙箱管理器（DockerSandboxManager）和交互式 Shell 运行器（InteractiveShellRunner）
- **File Scope**:
  - `healflow-engine/src/main/java/com/healflow/engine/git/**`
  - `healflow-engine/src/main/java/com/healflow/engine/workspace/**`
  - `healflow-engine/src/main/java/com/healflow/engine/sandbox/**`
  - `healflow-engine/src/main/java/com/healflow/engine/shell/**`
  - `healflow-engine/src/test/java/com/healflow/engine/**`
- **Dependencies**: depends on task-1, task-2
- **Test Command**: `mvn clean test -pl healflow-engine -Dtest=*Git*,*Workspace*,*Sandbox*,*Shell* --fail-at-end && mvn jacoco:report -pl healflow-engine`
- **Test Focus**:
  - JGit 克隆、分支、提交、推送操作
  - 工作空间隔离和清理
  - Docker 容器生命周期管理
  - Shell 命令执行和输出捕获
  - 异常场景处理（网络失败、权限不足）
  - 覆盖率 ≥90%

### Task 4: healflow-spring-boot-starter 自动配置
- **ID**: task-4
- **Description**: 在 healflow-spring-boot-starter 模块中实现 Spring Boot 自动配置类（HealflowAutoConfiguration）、配置属性类（HealflowProperties）、全局异常监听器（ExceptionListener）和 Git 属性加载器（GitPropertiesLoader），并配置 spring.factories
- **File Scope**:
  - `healflow-spring-boot-starter/src/main/java/com/healflow/starter/autoconfigure/**`
  - `healflow-spring-boot-starter/src/main/java/com/healflow/starter/properties/**`
  - `healflow-spring-boot-starter/src/main/java/com/healflow/starter/listener/**`
  - `healflow-spring-boot-starter/src/main/resources/META-INF/spring.factories`
  - `healflow-spring-boot-starter/src/test/java/com/healflow/starter/**`
- **Dependencies**: depends on task-1, task-2
- **Test Command**: `mvn clean test -pl healflow-spring-boot-starter -Dtest=*AutoConfiguration*,*Properties*,*Listener* --fail-at-end && mvn jacoco:report -pl healflow-spring-boot-starter`
- **Test Focus**:
  - 自动配置条件生效（@ConditionalOnProperty, @ConditionalOnClass）
  - 配置属性绑定和验证
  - 异常监听器捕获和处理
  - Git 属性加载（commit ID, branch, build time）
  - Spring Boot 应用上下文启动成功
  - 覆盖率 ≥90%

### Task 5: healflow-platform Web 入口
- **ID**: task-5
- **Description**: 在 healflow-platform 模块中实现 Spring Boot 主应用类（HealflowPlatformApplication）、REST 控制器（ReportController）和治愈服务（HealingService），集成 healflow-engine 和 healflow-spring-boot-starter
- **File Scope**:
  - `healflow-platform/src/main/java/com/healflow/platform/HealflowPlatformApplication.java`
  - `healflow-platform/src/main/java/com/healflow/platform/controller/**`
  - `healflow-platform/src/main/java/com/healflow/platform/service/**`
  - `healflow-platform/src/main/resources/application.yml`
  - `healflow-platform/src/test/java/com/healflow/platform/**`
- **Dependencies**: depends on task-1, task-2, task-3, task-4
- **Test Command**: `mvn clean test -pl healflow-platform -Dtest=*Controller*,*Service* --fail-at-end && mvn jacoco:report -pl healflow-platform`
- **Test Focus**:
  - REST API 端点可访问（POST /api/report, GET /api/status）
  - 请求参数验证和响应格式
  - HealingService 调用 engine 层逻辑
  - Spring Boot 应用完整启动
  - 集成测试覆盖主流程
  - 覆盖率 ≥90%

## 验收标准
- [ ] Maven Reactor 构建成功，模块依赖关系正确
- [ ] 所有 DTO/enum 类型定义完整，支持 JSON 序列化
- [ ] Git 操作、工作空间管理、Docker 沙箱功能可用
- [ ] Spring Boot 自动配置生效，配置属性可绑定
- [ ] Platform 模块 REST API 可访问，主流程打通
- [ ] 所有单元测试通过
- [ ] 代码覆盖率 ≥90%
- [ ] Maven Enforcer 规则校验通过（JDK 21, Maven 3.9+）

## 技术要点
- **JDK 21 特性**：使用 `--enable-preview` 编译参数，启用虚拟线程和模式匹配等特性
- **依赖管理**：父 POM 统一管理 Spring Boot BOM、JGit、Docker Java Client、JUnit 5 版本
- **模块隔离**：common 不依赖任何业务模块，engine 不依赖 Spring，starter 仅提供自动配置，platform 作为唯一可执行模块
- **测试策略**：单元测试使用 Mockito，集成测试使用 Testcontainers（Docker 沙箱场景），覆盖率通过 JaCoCo 插件统计
- **版本门禁**：Maven Enforcer Plugin 强制要求 JDK 21+ 和 Maven 3.9+，防止低版本环境构建失败
- **Git 属性**：使用 git-commit-id-plugin 在构建时注入 Git 元数据到 application.yml
