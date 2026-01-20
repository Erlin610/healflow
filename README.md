# HealFlow: AIOps Self-Healing Engine

![Language](https://img.shields.io/badge/Language-Java_21%2B-blue)
![Framework](https://img.shields.io/badge/Framework-Spring_Boot_3.x-green)
![Architecture](https://img.shields.io/badge/Architecture-Host_Container_Hybrid-orange)
![Sandbox](https://img.shields.io/badge/Sandbox-Docker-2496ED)

> **"Turn Runtime Exceptions into Merge Requests."**

## Project Overview | 项目简介

**HealFlow** 是一个面向 Spring Boot 应用的 AIOps 自愈引擎。它的目标不是“更聪明的日志分析”，而是把 Runtime Exception 转换为可执行的诊断/修复流程，并最终产出可 Review 的 Patch / Merge Request。

核心差异点（Key Concept）：HealFlow 采用 **"Agent Sandbox"** 模式——在隔离的 Docker Container 中运行 AI Agent（例如 Claude Code），由 Platform 负责工具授权、交互接管、代码同步与补丁导出。

---

## Quick Start | 快速开始

### Prerequisites | 前置要求

1. Java 21（确保 `JAVA_HOME` 指向 JDK 21；Spring Boot 3.x Maven Plugin 需要 Java 17+）
2. Maven 3.9+
3. Docker（Platform/Engine 需要，用于 Sandbox 执行）

建议先确认 Maven 实际使用的 Java 版本（以 `mvn -v` 为准），避免 `JAVA_HOME` 指向旧版 JDK。

### 1) Start Platform | 启动 healflow-platform

1. 从源码启动（开发态）：

```bash
# 注意：如果你的 JAVA_HOME 仍指向 JDK 8，会在执行 spring-boot 插件时报 UnsupportedClassVersionError
mvn -pl healflow-platform -am -DskipTests spring-boot:run
```

2. 默认监听端口：`8080`
3. Platform 的默认配置文件：`healflow-platform/src/main/resources/application.yml`

### 2) Integrate App | 接入你的应用（只需要引入 Starter）

1. 在业务项目中引入 Maven 依赖（直接 pom 引入即可使用）：

```xml
<dependency>
  <groupId>com.healflow</groupId>
  <artifactId>healflow-spring-boot-starter</artifactId>
  <version>0.0.1</version>
</dependency>
```

> 如果你还没有把 `0.0.1` 发布到 Maven 仓库，请先看 `Local Installation | 本地安装`（`mvn clean install` 安装到本地仓库）。

2. 在业务项目配置 `application.yml`：

```yaml
healflow:
  enabled: true
  # HealFlow Platform Base URL（不要包含路径）
  server-url: "http://localhost:8080"
  # 当前应用标识（建议唯一，用于 Platform 侧区分来源）
  app-id: "order-service"
  # 业务代码仓库信息（Platform 用于拉取源码进行分析/修复）
  git-url: "https://github.com/your-org/your-repo.git"
  git-branch: "main"
```

3. 启动业务应用后，触发一个未处理异常（unhandled exception / controller exception），Starter 会上报 Incident 到 Platform：
   - `POST {healflow.server-url}/api/v1/incidents/report`

### 3) Verify With Demo | 用 Demo 快速验证（可选）

1. 先启动 Platform（保持运行）
2. 再启动 Demo：

```bash
mvn -pl healflow-demo -am -Pboot -DskipTests spring-boot:run
```

3. 触发异常：

```bash
curl http://localhost:8081/trigger-error
```

---

## Usage | 使用指南

### Maven Dependency | 依赖引入

- 推荐只引入 `healflow-spring-boot-starter`；它会通过 `HealFlowAutoConfiguration` 自动注册上报组件（无需额外 `@Enable...`）。

### Configuration | 配置说明

| 字段名 (Property) | 类型 (Type) | 默认值 (Default) | 说明 (Description) |
|---|---|---|---|
| `healflow.enabled` | boolean | `true` | 是否启用 Starter；`false` 时不注册相关 Bean，也不会上报 |
| `healflow.server-url` | String | `http://localhost:8080` | Platform Base URL（不要包含路径） |
| `healflow.app-id` | String | (无) | 应用标识（建议唯一） |
| `healflow.git-url` | String | `""` | 业务仓库 URL（Platform 用于拉取源码） |
| `healflow.git-branch` | String | `main` | 默认分支（未配置时回退到 `main`） |

- 必填（Required）：`healflow.app-id`
- 建议（Recommended）：`healflow.git-url`、`healflow.git-branch`（用于 Platform 拉取源码做分析/修复）

### Configuration Example | 配置示例

```yaml
spring:
  application:
    name: order-service

healflow:
  enabled: true
  server-url: "http://healflow-platform.company.internal:8080"
  app-id: "${spring.application.name}"
  git-url: "git@github.com:your-org/order-service.git"
  git-branch: "main"
```

---

## Local Installation | 本地安装

当你还没有把 HealFlow 发布到 Maven 仓库时，可以先安装到本地仓库（local repository），然后在其他项目通过 `pom.xml` 直接引入。

### 1) Install To Local Repo | 安装到本地仓库

在本仓库根目录执行：

```bash
mvn -DskipTests clean install
```

### 2) Use In Another Project | 在其他项目中引用

在你的业务项目 `pom.xml` 中添加依赖（版本与本仓库一致）：

```xml
<dependency>
  <groupId>com.healflow</groupId>
  <artifactId>healflow-spring-boot-starter</artifactId>
  <version>0.0.1</version>
</dependency>
```

---

## Architecture | 架构说明

HealFlow 采用 "Host-Container Hybrid"（宿主机 + 容器混合）架构：在保证性能的同时实现安全隔离。

### Key Design Decisions | 核心设计

1. Hybrid Workspace（混合工作区）
   - Host（Platform）：使用 JGit/本地 Git 缓存源码，增量更新，避免每次重复 clone
   - Container（Sandbox）：通过 Volume Mount 把源码挂载进容器，Agent 在容器内改动，宿主机实时可见

2. Interactive Automation（交互式自动化）
   - AI Agent 通常是交互式 CLI（会询问授权/确认）
   - Platform 使用 Java ProcessBuilder 接管容器进程的 STDIN/STDOUT，通过规则进行自动应答（Auto-approve）或拦截高风险操作

3. Safety First（安全优先）
   - 编译/测试/文件修改等操作限制在容器内执行
   - 容器用完即焚（Ephemeral），避免环境污染

### Modules | 模块结构

```text
healflow-parent
├── healflow-common               # 公共 DTO / Enum
├── healflow-engine               # 核心引擎（Git + Sandbox + Shell Runner）
├── healflow-spring-boot-starter  # Client SDK（异常上报 & 自动配置）
├── healflow-platform             # Server（REST API + Orchestration）
└── healflow-demo                 # 示例应用
```

### Flow | 处理流程（简化）

1. Client（业务应用）捕获异常并构建 Incident
2. Starter 调用 Platform API：`/api/v1/incidents/report`
3. Platform 侧拉取/同步源码，启动 Sandbox
4. Agent 在 Sandbox 中分析并生成 Patch（Platform 导出 diff / patch）

---

## Development | 开发指南

### Build | 构建

```bash
# 编译（不跑测试）
mvn clean compile -DskipTests

# 单模块构建
mvn -pl healflow-platform -am clean package
```

### Test | 测试

```bash
# 运行测试
mvn clean test

# 完整校验（含覆盖率检查）
mvn clean verify
```
