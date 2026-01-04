# Project HealFlow: AIOps Self-Healing Engine

![Language](https://img.shields.io/badge/Language-Java_17%2B-blue)
![Framework](https://img.shields.io/badge/Framework-Spring_Boot_3.x-green)
![Architecture](https://img.shields.io/badge/Architecture-Host_Container_Hybrid-orange)
![Sandbox](https://img.shields.io/badge/Sandbox-Docker_%26_Testcontainers-2496ED)

> **"Turn Runtime Exceptions into Merge Requests."**
>
> **HealFlow** 是一个专为 Spring Boot 生态设计的 AIOps 自愈引擎。它不仅仅是分析日志，更是一个**全自动化的 DevOps 修复闭环**。
>
> **核心差异化**: 不同于传统的 API 调用，HealFlow 采用 **"Agent Sandbox"** 模式——在隔离的 Docker 容器中运行全功能的 AI 程序员（如 Claude Code），利用 Java 编排层自动处理工具授权与交互，安全地对真实项目源码进行诊断与修复。

---

## 🏗 System Architecture | 系统架构

本项目采用 **"Host-Container Hybrid" (宿主机-容器混合)** 架构，在保证极致性能的同时，实现绝对的安全隔离。

### 核心设计决策 (Key Design Decisions)

1.  **Hybrid Workspace (混合工作区)**:
    * **Host (Platform)**: 使用 `JGit` 在宿主机维护代码仓库。利用 `git fetch` 增量更新，避免每次诊断都重新 Clone，**解决网络效率问题**。
    * **Container (Sandbox)**: 启动 Docker 时通过 **Volume Mount (挂载)** 将宿主机的源码映射进容器。Agent 在容器内修改文件，宿主机实时同步。

2.  **Interactive Automation (交互式自动化)**:
    * AI Agent (如 Claude Code) 通常是交互式的（会询问用户确认权限）。
    * HealFlow Platform 使用 Java `ProcessBuilder` **劫持容器进程的 STDIN 和 STDOUT**，通过预设策略自动批准（Auto-approve）常规操作或拦截高危操作。

3.  **Safety First (安全优先)**:
    * 所有 AI 操作（编译、运行测试、修改文件）均限制在 Docker 容器内。
    * 容器用完即焚（Ephemeral Containers），防止环境污染。

---

## 📂 Project Structure | 项目结构

```text
healflow-root
├── healflow-starter      # [Client SDK] 嵌入业务项目的探针，负责抓取异常与 CommitID
├── healflow-platform     # [Server Core] 核心服务 (Spring Boot Web)
├── healflow-engine       # [The Brain] 核心引擎模块
│   ├── git               # JGit 实现的源码管理器 (Host side)
│   └── sandbox           # Testcontainers 实现的沙箱运行器与交互劫持逻辑 (Docker side)
├── healflow-common       # [Shared] 公共 DTO (Incident, PatchProposal)
└── README.md


🚀 Getting Started | 快速开始
请按照以下步骤启动项目并进行集成测试。

1. Prerequisites (环境要求)
JDK 17+: 核心开发语言。

Maven 3.8+: 项目构建工具。

Docker: [必须安装] 部署 HealFlow Platform 的服务器必须安装 Docker，用于启动隔离沙箱。

Agent Tools: 基础镜像需预装 Agent (如 claude-code)，且需配置访问凭证 (如 ANTHROPIC_API_KEY)。

2. Platform Setup (服务端部署)
Bash

# 1. 克隆本仓库
git clone [https://github.com/your-org/healflow.git](https://github.com/your-org/healflow.git)

# 2. 修改配置 (healflow-platform/src/main/resources/application.yml)
# 重点配置 Git 访问令牌和 Docker 镜像策略
# healflow.sandbox.image: "ubuntu:latest" (或预装了 claude-code 的自定义镜像)

# 3. 启动平台服务
cd healflow-platform
mvn spring-boot:run
3. Client Integration (业务接入)
在您的 Spring Boot 业务应用中执行以下两步：

Step 1: 引入 SDK 依赖 (pom.xml)

XML

<dependency>
    <groupId>com.healflow</groupId>
    <artifactId>healflow-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
Step 2: 配置探针 (application.yml)

YAML

healflow:
  enabled: true
  # HealFlow Platform 的部署地址
  server-url: "http://localhost:8080" 
  # 当前应用标识
  app-id: "order-service"
  # 源码仓库信息 (用于 Agent 拉取代码)
  project:
    git-url: "git@gitlab.com:finance/order-service.git"
    branch: "main"
🛠 Deep Dive: How It Works? | 核心原理
Stage 1: The Trap (捕获)
当业务系统抛出未捕获异常：

HealflowProbe 拦截 Global Exception。

读取 git.properties (需配置 Maven Git Commit ID Plugin) 获取发生报错时的精确 Commit ID。

打包 Context (Stacktrace + CommitID + Env Vars) 发送给 Platform。

Stage 2: The Setup (准备)
Platform 收到请求：

Git Manager: 检查本地缓存。如果仓库存在，执行 git fetch && git reset --hard {commitId}；如果不存在，执行 git clone。

Sandbox Init: 启动 Docker 容器，将本地源码目录挂载到容器的 /src。

Stage 3: The Interrogation (交互式诊断)
这是最精彩的部分。Platform 启动 Agent (Claude CLI) 并接管控制台 IO：

Java

// 核心逻辑伪代码演示 (Located in healflow-engine)
ProcessBuilder pb = new ProcessBuilder("docker", "exec", "claude", "analyze", "/src");
Process process = pb.start();

// 监听 Agent 的提问 (STDOUT)
while ((line = reader.readLine()) != null) {
    if (line.contains("Allow read access to UserServiceImpl.java? [y/N]")) {
        // Platform 自动输入 'y' (STDIN)
        writer.write("y"); 
        writer.flush();
        log.info("Auto-approved read access for Agent.");
    }
    else if (line.contains("Delete file application.yml?")) {
        // 拦截高危操作
        writer.write("n");
        writer.flush();
        log.warn("Blocked attempt to delete config file.");
    }
}
Stage 4: The Patch (补丁)
Agent 在容器内完成代码修改。

Platform 在宿主机执行 git diff 生成 .patch 文件。

通过 IM/Web 通知开发者进行 Code Review。

开发者批准后，Platform 执行 git push 并自动创建 Merge Request。

⚠️ Security Guidelines | 安全准则
Network Isolation: 建议生产环境的 Docker 容器配置为 network: limited，仅允许访问必要的 Maven/Pip 源，防止代码或密钥外泄。

Token Management: 所有的 API Keys 应以环境变量形式在启动容器时注入，禁止硬编码。

Human in the Loop: 只有经过人工点击 "Approve" 的代码才会被 Push 到远程仓库。

🗓 Roadmap | 开发计划
[ ] Phase 1: MVP (The Analyst)

[ ] 完成 Spring Boot Starter 异常捕获与上报。

[ ] 完成 Platform 基础 JGit 封装 (Clone/Pull)。

[ ] 实现 Java ProcessBuilder 调用本地 Shell (Mock Agent) 并打通 IO 劫持。

[ ] Phase 2: Alpha (The Fixer)

[ ] 引入 Testcontainers 实现 Docker 沙箱生命周期管理。

[ ] 完善 "交互式 CLI" 的自动应答器 (Auto-Responder) 策略。

[ ] 集成 Claude Code / OpenAI CLI 真实环境。

[ ] Phase 3: Release (The Closer)

[ ] Web 控制台：在线查看 Code Diff。

[ ] GitLab/GitHub API 深度集成 (Auto PR)。

Maintainers
Tech Lead: [Your Name]

Team: Backend Architecture Group
