# Incident Persistence - Development Plan

## Overview
为 Incident 引入 JPA 持久化机制，替换内存 Map，支持状态流转（PENDING → ANALYZING → ANALYZED → FIXING → FIXED/FAILED）。

## Task Breakdown

### Task 1: 平台持久化基础设施
- **ID**: task-1
- **Type**: default
- **Description**: 在 healflow-platform 模块引入 Spring Data JPA 和 H2 数据库依赖，配置数据源、JPA 属性和 Hibernate DDL 自动建表策略
- **File Scope**:
  - `healflow-platform/pom.xml`
  - `healflow-platform/src/main/resources/application.yml`
- **Dependencies**: None
- **Test Command**: `mvn clean test -pl healflow-platform -Dtest=*Test -Djacoco.skip=false jacoco:report`
- **Test Focus**:
  - Spring Boot 应用上下文正常启动
  - DataSource Bean 正确注入
  - H2 数据库连接成功
  - JPA EntityManagerFactory 初始化无异常

### Task 2: Incident 持久化与状态机
- **ID**: task-2
- **Type**: default
- **Description**: 创建 IncidentEntity（包含 id、appId、status、sessionId、timestamps 等字段）、IncidentRepository 接口，改造 IncidentService 和 IncidentController 使用 JPA 持久化，实现状态流转逻辑（PENDING → ANALYZING → ANALYZED → FIXING → FIXED/FAILED）
- **File Scope**:
  - `healflow-platform/src/main/java/com/healflow/platform/entity/IncidentEntity.java` (新建)
  - `healflow-platform/src/main/java/com/healflow/platform/entity/IncidentStatus.java` (新建)
  - `healflow-platform/src/main/java/com/healflow/platform/repository/IncidentRepository.java` (新建)
  - `healflow-platform/src/main/java/com/healflow/platform/service/IncidentService.java`
  - `healflow-platform/src/main/java/com/healflow/platform/controller/IncidentController.java`
- **Dependencies**: depends on task-1
- **Test Command**: `mvn clean test -pl healflow-platform -Dtest=IncidentServiceTest,IncidentRepositoryTest -Djacoco.skip=false jacoco:report`
- **Test Focus**:
  - IncidentEntity 正确映射到数据库表
  - IncidentRepository CRUD 操作正常
  - 状态流转逻辑正确（PENDING → ANALYZING → ANALYZED → FIXING → FIXED/FAILED）
  - analyzeIncident/generateFix/applyFix 方法正确更新状态和 sessionId
  - 并发场景下状态一致性（乐观锁或悲观锁）

### Task 3: 测试与兼容性收尾
- **ID**: task-3
- **Type**: quick-fix
- **Description**: 更新 IncidentControllerTest 和 IncidentServiceTest，适配 JPA 持久化逻辑，使用 @DataJpaTest 或 @SpringBootTest 进行集成测试，确保所有单元测试通过且覆盖率 ≥90%
- **File Scope**:
  - `healflow-platform/src/test/java/com/healflow/platform/controller/IncidentControllerTest.java`
  - `healflow-platform/src/test/java/com/healflow/platform/service/IncidentServiceTest.java`
  - `healflow-platform/src/test/java/com/healflow/platform/repository/IncidentRepositoryTest.java` (新建)
- **Dependencies**: depends on task-2
- **Test Command**: `mvn clean test -pl healflow-platform jacoco:report && mvn jacoco:check -pl healflow-platform -Djacoco.haltOnFailure=true -Djacoco.minimum=0.90`
- **Test Focus**:
  - Controller 层 REST API 测试（POST /incidents、GET /incidents/{id}、GET /incidents/{id}/status）
  - Service 层业务逻辑测试（状态流转、异常处理）
  - Repository 层数据访问测试（findByAppId、findByStatus、自定义查询）
  - 边界条件测试（空值、重复提交、非法状态转换）
  - 代码覆盖率达到 90% 以上

## Acceptance Criteria
- [ ] Spring Data JPA 和 H2 数据库依赖正确引入
- [ ] IncidentEntity 和 IncidentRepository 正确实现
- [ ] Incident 状态流转逻辑正确（PENDING → ANALYZING → ANALYZED → FIXING → FIXED/FAILED）
- [ ] IncidentService 和 IncidentController 完成 JPA 持久化改造
- [ ] 所有单元测试通过（mvn test）
- [ ] 代码覆盖率 ≥90%（JaCoCo 报告验证）
- [ ] 应用启动时自动建表（Hibernate DDL auto）
- [ ] 支持通过 appId、status 查询 Incident

## Technical Notes
- **数据库选型**: 开发环境使用 H2 内存数据库，生产环境可切换到 MySQL（通过 Spring Profile 配置）
- **状态机设计**: 使用 Enum 定义 IncidentStatus，在 Service 层实现状态转换校验逻辑
- **并发控制**: 使用 JPA @Version 注解实现乐观锁，防止并发更新冲突
- **向后兼容**: 保留现有 IncidentReport DTO，Entity 与 DTO 之间通过 Mapper 转换
- **测试策略**: Repository 层使用 @DataJpaTest，Service 层使用 @SpringBootTest + @Transactional，Controller 层使用 @WebMvcTest
- **DDL 策略**: 开发环境使用 `spring.jpa.hibernate.ddl-auto=create-drop`，生产环境使用 `validate` 或 Flyway/Liquibase 管理
