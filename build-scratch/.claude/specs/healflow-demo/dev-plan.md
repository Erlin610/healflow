# healflow-demo - 开发计划

## 概述
创建最小化 Spring Boot 测试项目，验证 Phase 1 异常上报链路功能。

## 任务拆解

### 任务 1：创建 healflow-demo 模块
- **ID**: task-1
- **描述**: 创建完整的 Spring Boot Web 项目，包含 Maven 配置、应用入口、测试端点和 HealFlow 配置
- **文件范围**:
  - `healflow-demo/pom.xml`
  - `healflow-demo/src/main/java/com/healflow/demo/DemoApplication.java`
  - `healflow-demo/src/main/java/com/healflow/demo/controller/DemoController.java`
  - `healflow-demo/src/main/resources/application.yml`
  - `pom.xml` (根 POM，添加 healflow-demo 模块声明)
- **依赖**: None
- **验证命令**: `mvn -pl healflow-demo -am -DskipTests clean package`
- **验证重点**:
  - Maven 构建成功，生成可执行 JAR
  - Spring Boot 应用可启动（端口 8081）
  - GET /trigger-error 端点可访问并抛出预期异常
  - HealFlow starter 自动配置生效

## 验收标准
- [ ] healflow-demo 模块成功创建并集成到父 POM
- [ ] Spring Boot 应用可正常启动
- [ ] GET /trigger-error 端点返回 500 错误并抛出 "Test HealFlow" 异常
- [ ] application.yml 正确配置 app-id 和 server-url
- [ ] Maven 构建命令执行成功（跳过测试）
- [ ] 依赖 healflow-spring-boot-starter 正确引入

## 技术要点
- **Spring Boot 版本**: 3.3.6
- **JDK 版本**: 21
- **应用端口**: 8081（避免与 healflow-platform 的 8080 冲突）
- **最小化原则**: 仅包含验证异常上报所需的最少代码
- **测试策略**: 跳过单元测试（-DskipTests），通过手动触发端点验证功能
- **配置约束**:
  - app-id 固定为 "demo-service-01"
  - server-url 指向本地 healflow-platform (http://localhost:8080)
