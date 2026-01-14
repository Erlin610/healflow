# Phase 6 - The Governor (治理与生命周期) - Development Plan

## Overview
实现智能去重、生命周期状态机、应用配置中心和 Webhook 通知，将 HealFlow 从"报错喇叭"升级为"有记忆、懂分寸的数字员工"。

## Task Breakdown

### Task 1: Error Fingerprint & Deduplication
- **ID**: task-1
- **Type**: default
- **Description**: 创建 ErrorFingerprint 实体和 FingerprintService，实现基于 ExceptionType + RootStackTrace 的指纹生成算法（SHA-256 哈希），修改 IncidentService 在创建 Incident 前先查询指纹是否存在，若存在则增加 occurrenceCount 和更新 lastSeenTime，不触发 AI 分析
- **File Scope**: healflow-platform/src/main/java/com/healflow/platform/entity/ErrorFingerprintEntity.java, healflow-platform/src/main/java/com/healflow/platform/repository/ErrorFingerprintRepository.java, healflow-platform/src/main/java/com/healflow/platform/service/FingerprintService.java, healflow-platform/src/test/java/com/healflow/platform/service/
- **Dependencies**: None
- **Test Command**: mvn -pl healflow-platform clean test -Dtest=FingerprintServiceTest -Djacoco.skip=true
- **Test Focus**: 验证相同堆栈生成相同指纹；验证不同堆栈生成不同指纹；验证动态参数（如 ID）不影响指纹；测试首次命中创建新记录，重复命中仅更新计数

### Task 2: Lifecycle State Machine
- **ID**: task-2
- **Type**: default
- **Description**: 在 IncidentEntity 添加 status 字段（枚举：OPEN/ANALYZING/PENDING_REVIEW/FIXED/REGRESSION/IGNORED），创建 IncidentStatus 枚举类，修改 IncidentService 实现状态流转逻辑（OPEN→ANALYZING→PENDING_REVIEW→FIXED，FIXED 再次命中变 REGRESSION），添加状态变更时间戳字段 statusChangedAt
- **File Scope**: healflow-platform/src/main/java/com/healflow/platform/entity/IncidentEntity.java, healflow-common/src/main/java/com/healflow/common/enums/IncidentStatus.java, healflow-platform/src/main/java/com/healflow/platform/service/IncidentService.java, healflow-platform/src/test/java/com/healflow/platform/service/
- **Dependencies**: depends on task-1
- **Test Command**: mvn -pl healflow-platform clean test -Dtest=IncidentServiceTest -Djacoco.skip=true
- **Test Focus**: 验证新 Incident 初始状态为 OPEN；验证 AI 分析启动后状态变为 ANALYZING；验证 FIXED 状态的 Incident 再次命中变为 REGRESSION；测试状态变更时间戳正确记录

### Task 3: Application Configuration & Secrets Management
- **ID**: task-3
- **Type**: default
- **Description**: 创建 ApplicationEntity（包含 appName/gitUrl/gitBranch/gitToken/aiApiKey/autoAnalyze/autoFixProposal/autoCommit/webhookUrl），创建 ApplicationRepository 和 ApplicationService，实现敏感字段（gitToken/aiApiKey）的 AES-256 加密存储和解密读取，添加 REST API（ApplicationController）支持 CRUD 操作，返回前端时敏感字段掩码显示
- **File Scope**: healflow-platform/src/main/java/com/healflow/platform/entity/ApplicationEntity.java, healflow-platform/src/main/java/com/healflow/platform/repository/ApplicationRepository.java, healflow-platform/src/main/java/com/healflow/platform/service/ApplicationService.java, healflow-platform/src/main/java/com/healflow/platform/controller/ApplicationController.java, healflow-platform/src/main/java/com/healflow/platform/util/EncryptionUtil.java, healflow-platform/src/test/java/com/healflow/platform/service/
- **Dependencies**: None
- **Test Command**: mvn -pl healflow-platform clean test -Dtest=ApplicationServiceTest,EncryptionUtilTest -Djacoco.skip=true
- **Test Focus**: 验证敏感字段加密后无法直接读取明文；验证解密后能正确还原原始值；测试 API 返回时敏感字段显示为掩码；验证应用配置的增删改查功能

### Task 4: Webhook Notification System
- **ID**: task-4
- **Type**: default
- **Description**: 创建 WebhookService，实现通知发送逻辑（支持钉钉/飞书/Slack 格式），在 IncidentService 中集成 Webhook 调用（新 Incident 创建时、状态变为 REGRESSION 时触发），支持通知策略配置（仅新问题/包含回归），添加重试机制（最多 3 次，指数退避）
- **File Scope**: healflow-platform/src/main/java/com/healflow/platform/service/WebhookService.java, healflow-platform/src/main/java/com/healflow/platform/service/IncidentService.java, healflow-platform/src/main/java/com/healflow/platform/dto/WebhookPayload.java, healflow-platform/src/test/java/com/healflow/platform/service/
- **Dependencies**: depends on task-2, depends on task-3
- **Test Command**: mvn -pl healflow-platform clean test -Dtest=WebhookServiceTest -Djacoco.skip=true
- **Test Focus**: 验证 Webhook 请求格式符合钉钉/飞书规范；测试新 Incident 触发通知；测试 REGRESSION 状态触发高优告警；验证重试机制在失败时正确执行

### Task 5: UI Updates - Dashboard & Configuration Pages
- **ID**: task-5
- **Type**: ui
- **Description**: 更新 index.html，添加应用配置页面（表单包含 Git 配置/密钥输入/自动化策略开关/Webhook 配置），修改 Incident 列表页显示状态 Badge（颜色编码：OPEN 蓝/ANALYZING 黄/PENDING_REVIEW 橙/FIXED 绿/REGRESSION 红），添加 Incident 详情页（显示指纹 ID/发生次数/时间轴/堆栈/AI 分析结果/操作按钮），实现前后端 API 对接
- **File Scope**: healflow-platform/src/main/resources/static/index.html, healflow-platform/src/main/resources/static/css/**, healflow-platform/src/main/resources/static/js/**
- **Dependencies**: depends on task-2, depends on task-3, depends on task-4
- **Test Command**: mvn -pl healflow-platform spring-boot:run
- **Test Focus**: 手动测试应用配置表单提交成功；验证敏感字段输入后显示为掩码；测试 Incident 列表状态 Badge 颜色正确；验证详情页数据完整显示；测试操作按钮（Generate Fix/Apply Fix/Mark as Fixed/Ignore）功能正常

## Acceptance Criteria
- [ ] 相同错误指纹的重复报错不触发 AI 分析，仅更新计数
- [ ] Incident 状态机正确流转（OPEN→ANALYZING→PENDING_REVIEW→FIXED）
- [ ] FIXED 状态的 Incident 再次命中时变为 REGRESSION 并触发高优告警
- [ ] 应用配置支持 Git 仓库、密钥、自动化策略和 Webhook 配置
- [ ] 敏感字段（Git Token/AI API Key）加密存储，前端显示为掩码
- [ ] Webhook 通知在新 Incident 和 REGRESSION 时正确发送
- [ ] UI 显示应用配置页面、Incident 状态 Badge 和详情页
- [ ] 所有单元测试通过
- [ ] 代码覆盖率 ≥90%

## Technical Notes
- 指纹算法：提取 ExceptionType + 业务代码前 3 帧堆栈，使用 SHA-256 生成哈希
- 去重策略：首次命中创建 Incident + 触发 AI，重复命中仅计数，ANALYZING 状态期间的重复报错直接丢弃（防止 Docker 资源耗尽）
- 状态机保护：FIXED→REGRESSION 转换时，AI Prompt 需包含"此问题曾被修复但复发"上下文
- 加密方案：使用 AES-256-GCM 模式，密钥从环境变量读取（HEALFLOW_ENCRYPTION_KEY），密钥不存在时启动失败
- H2 数据库：使用文件模式（jdbc:h2:file:./data/healflow），持久化所有配置和状态
- Webhook 格式：支持钉钉（Markdown）、飞书（富文本卡片）、Slack（Blocks），根据 URL 前缀自动识别
- UI 技术栈：纯 HTML/CSS/JavaScript，使用 Fetch API 调用后端 REST 接口，状态 Badge 使用 CSS 类实现颜色编码
- 向后兼容：新增字段允许为 null，现有 Incident 数据迁移时自动设置默认值（status=OPEN, occurrenceCount=1）
