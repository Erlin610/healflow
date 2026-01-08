# API Key 配置指南

## 问题
测试运行时报错：`No valid API key found in ~/.claude/settings.json`

## 解决方案

API Key 可以通过以下三种方式配置（按优先级排序）：

### 方式 1: 环境变量 ANTHROPIC_API_KEY（推荐）
```bash
# Windows PowerShell
$env:ANTHROPIC_API_KEY="your-token-here"

# Windows CMD
set ANTHROPIC_API_KEY=your-token-here

# Linux/Mac
export ANTHROPIC_API_KEY="your-token-here"
```

### 方式 2: 环境变量 ANTHROPIC_AUTH_TOKEN
```bash
# Windows PowerShell
$env:ANTHROPIC_AUTH_TOKEN="your-token-here"

# Windows CMD
set ANTHROPIC_AUTH_TOKEN=your-token-here

# Linux/Mac
export ANTHROPIC_AUTH_TOKEN="your-token-here"
```

### 方式 3: ~/.claude/settings.json 文件

创建或编辑 `~/.claude/settings.json`（Windows: `C:\Users\你的用户名\.claude\settings.json`）：

```json
{
  "env": {
    "ANTHROPIC_AUTH_TOKEN": "your-token-here"
  }
}
```

或者使用 ANTHROPIC_API_KEY：

```json
{
  "env": {
    "ANTHROPIC_API_KEY": "your-token-here"
  }
}
```

## 支持的 JSON 字段（按查找顺序）

代码会按以下顺序查找 API Key：

1. `/env/ANTHROPIC_AUTH_TOKEN`
2. `/env/ANTHROPIC_API_KEY`
3. `/env/anthropicApiKey`
4. `/env/anthropic_api_key`
5. `/ANTHROPIC_AUTH_TOKEN`
6. `/ANTHROPIC_API_KEY`
7. `/anthropicApiKey`
8. `/anthropic_api_key`
9. `/apiKey`

## 验证配置

### 检查环境变量
```bash
# Windows PowerShell
echo $env:ANTHROPIC_API_KEY
echo $env:ANTHROPIC_AUTH_TOKEN

# Linux/Mac
echo $ANTHROPIC_API_KEY
echo $ANTHROPIC_AUTH_TOKEN
```

### 检查 settings.json
```bash
# Windows
type %USERPROFILE%\.claude\settings.json

# Linux/Mac
cat ~/.claude/settings.json
```

## 运行测试

配置好 API Key 后，重新运行测试：

```bash
mvn test -Dtest=ClaudeCodeIntegrationTest -pl healflow-platform
```

## 注意事项

1. **环境变量优先**: 如果同时设置了环境变量和 settings.json，环境变量会被优先使用
2. **MiniMax API**: 如果使用 MiniMax API，确保 settings.json 包含正确的 `ANTHROPIC_BASE_URL`
3. **安全性**: 不要将 API Key 提交到 Git 仓库
4. **Docker 容器**: 容器内的 API Key 通过环境变量注入（见 `executeInteractiveRunInSandbox` 调用）
