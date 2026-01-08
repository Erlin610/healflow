# Claude Code 集成测试

## 测试文件
`ClaudeCodeIntegrationTest.java` - 验证与 Docker 内 Claude Code 的交互

## 前置条件

### 1. 构建 Docker 镜像
```bash
cd healflow-engine/src/main/resources/docker
docker build -t healflow-agent:v1 .
```

### 2. 验证镜像
```bash
docker images | grep healflow-agent
# 应该看到: healflow-agent   v1   ...
```

### 3. 配置 API Key

**方式 1: 环境变量（推荐）**
```bash
export ANTHROPIC_API_KEY="your-api-key"
```

**方式 2: settings.json**
已配置在 `healflow-engine/src/main/resources/docker/config/settings.json`

## 运行测试

### 启用测试
移除 `@Disabled` 注解或使用 IDE 强制运行

### Maven 命令
```bash
# 运行单个测试
mvn test -Dtest=ClaudeCodeIntegrationTest -pl healflow-platform

# 运行所有集成测试
mvn test -Dtest=*IntegrationTest -pl healflow-platform
```

### IDE 运行
1. 打开 `ClaudeCodeIntegrationTest.java`
2. 右键 `claudeCodeSaysHello()` 方法
3. 选择 "Run 'claudeCodeSaysHello()'"

## 预期输出

### 成功示例
```
Claude Code Response: Hello! I'm Claude, nice to meet you.
```

### 失败排查

**错误: "No API key found"**
- 检查环境变量: `echo $ANTHROPIC_API_KEY`
- 检查 settings.json: `cat ~/.claude/settings.json`

**错误: "Image not found: healflow-agent:v1"**
- 重新构建镜像（见前置条件）

**错误: "Docker daemon not running"**
- 启动 Docker Desktop 或 Docker 服务

**错误: "Connection timeout"**
- 检查网络连接
- 检查 API 端点配置（settings.json 中的 ANTHROPIC_BASE_URL）

## 测试原理

1. **启动容器**: 使用 `healflow-agent:v1` 镜像
2. **执行命令**: `claude -p "Say hello in one sentence" --output-format json --max-turns 1`
3. **解析输出**: 从 JSON 的 `result` 字段提取响应
4. **验证内容**: 断言包含 "hello" 关键词

## 扩展测试

可以基于此模板创建更多测试：

```java
@Test
void claudeCodeAnalyzesCode() throws Exception {
    List<String> command = List.of(
        "claude", "-p", "List all Java files in current directory",
        "--allowedTools", "Glob",
        "--output-format", "json",
        "--max-turns", "2"
    );
    // ... 执行和验证
}
```

## 注意事项

1. **成本**: 每次测试会调用 Claude API，产生费用
2. **速度**: 集成测试较慢（1-2分钟），不适合频繁运行
3. **隔离**: 使用临时容器名避免冲突
4. **清理**: DockerSandboxManager 会自动清理容器（--rm 参数）
