# 需求说明 0113-page-design.md

页面重构设计
你需要知道这个项目到底是做什么的。其需要展示在页面上的是什么内容

首先：
这个项目是展示给开发者或者技术经理看的一个bug管理监控页面，所以需要有bug列表，但是如何呈现？ 需要你思考设计
这样就需要有显示什么字段？ 用户关心什么字段？ 状态？ 如何排版？ 什么主题 等等

我觉得目前需要的内容：
至少需要 应用管理，这里肯定是多应用，不是单应用，也会有实例数一说。一个应用部署多个实例探针，应用管理 至少应该处理 git仓库 、 分支、token、webhook等等吧
缺陷管理 这里就要显示对应的缺陷内容了，需要提醒的是，每一个缺陷有一个指纹id的说法，多个缺陷可能是一个指纹。那么这个如何交互，如何设计。

现在的界面看着太low了。太AI了。不要这种太AI的感觉。并且你可以提一些要求，如果需要呈现什么。目前还没有的，你也可以提出来
---
# HealFlow UI Design Specs v1.0

## 1. 设计原则 (Design Principles)
*   **Developer-First (开发者优先)**: 界面应类似 IDE 或 CI/CD 控制台，而非营销落地页。强调信息密度与操作效率。
*   **Function over Form (功能至上)**: 减少装饰性元素（大阴影、大圆角、磨砂玻璃），使用清晰的边框、分割线和高对比度文字。
*   **Status Visibility (状态显性化)**: 事故的处理状态（分析中、待审核、已修复）是核心关注点，需通过颜色与图标直观呈现。

## 2. 视觉风格 (Visual Style)
*   **Typography**:
    *   UI字体: Inter / 系统默认 sans-serif (清晰、中性)
    *   代码字体: JetBrains Mono / Fira Code (等宽，便于阅读堆栈和Diff)
*   **Colors (Semantic)**:
    *   Primary: `#2563EB` (Tech Blue - 操作/链接)
    *   Success: `#16A34A` (Green - 已修复/自动提交)
    *   Warning: `#D97706` (Amber - 待审核/分析中)
    *   Danger: `#DC2626` (Red - 报错/失败)
    *   Neutral: `#F3F4F6` (背景), `#FFFFFF` (卡片), `#E5E7EB` (边框)
*   **Components**:
    *   **Card**: 白色背景，1px 灰色边框 (`#E5E7EB`)，无阴影或极微弱阴影 (`shadow-sm`)，圆角 `6px`。
    *   **Button**: 实心主色按钮（强调操作），描边次级按钮（辅助操作）。高度 `32px` (Compact) 或 `36px` (Standard)。
    *   **Badge**: 柔和背景色 + 深色文字 (e.g., `bg-red-50 text-red-700`)，胶囊型。

## 3. 页面详细设计 (Page Details)

### 3.1 应用概览 (App Dashboard)
**目标**: 展示监控的应用及其健康/配置状态，管理接入源。

*   **布局**: 顶部统计栏 + 卡片网格/列表。
*   **统计栏**: 总应用数、今日异常数、待处理修复。
*   **应用卡片 (Application Card)**:
    *   **Header**: 应用名称 (`appName`) [加粗, 16px] + 状态指示灯。
    *   **Body**:
        *   Git Info: `Icon(Git)` `gitUrl` (截断显示) / `branch`
        *   Automation Tag: `Auto-Analyze` `Auto-Fix` (作为 Tags 展示，开启为亮色，关闭为灰显)
    *   **Footer**:
        *   "Last update: 2 mins ago"
        *   Actions: [Settings] [View Incidents]

### 3.2 异常控制台 (Incident Console / List)
**目标**: 快速筛选、定位高优先级问题。

*   **布局**: 紧凑型数据表格 (Data Grid)。
*   **过滤器 (Toolbar)**:
    *   下拉选: 应用 (App)、状态 (Status)、环境 (Env)。
    *   搜索框: 搜索 Error Message 或 Fingerprint ID。
*   **表格列 (Columns)**:
    1.  **Status**: 图标 + 颜色 (e.g., 🔴 Open, 🟡 Analyzing, 🟢 Fixed)。
    2.  **Fingerprint**: 指纹ID (短码)，显示聚合数量 (e.g., "NPE-8x2d (5 occurrences)")。
    3.  **Severity/Type**: `errorType` (e.g., `NullPointerException`) [红色代码字]。
    4.  **Message**: `errorMessage` (首行截断，Hover 显示全文)。
    5.  **Context**: 应用名 + 分支。
    6.  **Time**: `occurredAt` (Relative time, e.g., "5m ago")。
    7.  **Actions**: [Analyze] [Quick Fix]。
*   **交互**:
    *   点击行进入详情页。
    *   支持多选批量操作（如 "Mark as Ignored"）。

### 3.3 异常工作台 (Incident Workbench / Detail)
**目标**: 沉浸式诊断与修复。IDE 风格布局。

*   **Header**:
    *   Breadcrumb: Incidents / {ID}
    *   Title: `{errorType}: {errorMessage}` (大号字体)
    *   Right Actions: [Re-Analyze] [Generate Fix] [Apply Fix] [Ignore]
*   **Grid Layout (三栏布局)**:
    *   **Left Sidebar (Metadata) - 250px**:
        *   **Incident Info**: ID, Time, Container Name.
        *   **Git Context**: Commit ID (链接到 Git), Branch, Repo.
        *   **Environment**: Java Version, Spring Profile, Custom Env Vars.
    *   **Main Content (Tabs) - Flexible**:
        *   **Tab 1: Stack Trace (Default)**
            *   Monaco Editor (Read-only)。
            *   高亮显示项目源码行，灰显框架代码。
        *   **Tab 2: AI Analysis**
            *   Markdown 渲染区域。
            *   结构化展示：Root Cause, Impact, Suggested Approach。
        *   **Tab 3: Fix Proposal (Diff)**
            *   Side-by-side Diff View (类似 GitHub PR)。
            *   显示 `changedFiles` 和 `gitDiff`。
            *   底部浮动栏: [Approve & Merge] [Reject]。
    *   **Right Panel (Activity & Chat) - 300px**:
        *   **Timeline**: 垂直时间轴，记录状态变更 (Open -> Analyzing -> Fix Proposed)。
        *   **Agent Interaction**: 如果是交互式修复，显示 Agent 的提问（"需授权读取 FileX..."）和用户的回答。

## 4. 关键交互流程 (Key Interactions)

1.  **从列表到详情**:
    *   点击列表中的 Fingerprint，若有聚合，展开子列表；点击具体 Incident 进入 Workbench。

2.  **修复流程 (The Healing Flow)**:
    *   用户点击 "Generate Fix"。
    *   UI 变更为 "Processing" 状态，显示实时日志 (Stream Log) 或进度条。
    *   完成后，自动切换到 "Fix Proposal" Tab，展示 Diff。
    *   用户点击 "Approve"，触发后端提交合并请求，UI 显示 "Merging..." -> "Fixed"。

3.  **指纹聚合**:
    *   列表页默认按 `fingerprintId` 聚合展示。
    *   提供 "Expand" 按钮查看该指纹下的所有具体报错实例。

