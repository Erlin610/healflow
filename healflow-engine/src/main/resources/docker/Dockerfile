# 使用 Node.js 20 轻量版作为基础
FROM node:20-bullseye-slim

# 安装基础工具
# git: 必须，AI 需要看 git 历史
# ripgrep (rg): 必须，claude-code 依赖的高效搜索
# dumb-init: 进程守护，防止容器僵死
RUN apt-get update && apt-get install -y \
    git \
    curl \
    ripgrep \
    dumb-init \
    && rm -rf /var/lib/apt/lists/*

# 全局安装 claude-code
RUN npm install -g @anthropic-ai/claude-code

# 创建配置目录结构 (Claude Code 默认读取 /root/.claude)
RUN mkdir -p /root/.claude

# 注入配置：配置权限策略
COPY config/settings.json /root/.claude/settings.json

# 设置工作目录 (将挂载宿主机源码)
WORKDIR /src

# 环境变量声明 (运行时注入)
ENV ANTHROPIC_API_KEY=""
ENV CI=true
ENV CLAUDE_NO_UPDATE_NOTIFIER=true

# 启动入口
ENTRYPOINT ["/usr/bin/dumb-init", "--"]
# 默认保持空转，等待 Java 调用 exec 或 run
CMD ["tail", "-f", "/dev/null"]
