# 贡献指南

感谢你对本项目的关注！我们欢迎任何形式的贡献。

## 贡献方式

### 报告 Bug

如果发现 Bug，请通过 [GitHub Issues](https://github.com/liuqitoday/smart-accounting-assistant/issues) 报告，并包含：

- 问题描述
- 复现步骤
- 预期行为 vs 实际行为
- 环境信息（OS、Java 版本、浏览器等）
- 相关日志或截图

### 提出功能建议

通过 [GitHub Discussions](https://github.com/liuqitoday/smart-accounting-assistant/discussions) 提出新功能建议，描述：

- 功能需求
- 使用场景
- 期望的实现方式

### 贡献代码

1. **Fork 本仓库**

2. **创建特性分支**

```bash
git checkout -b feature/your-feature-name
```

3. **编写代码**

   - 遵循现有代码风格
   - 添加必要的单元测试
   - 更新相关文档

4. **提交更改**

```bash
git add .
git commit -m "feat: add your feature description"
```

提交信息格式：

- `feat:` - 新功能
- `fix:` - Bug 修复
- `docs:` - 文档更新
- `style:` - 代码格式调整
- `refactor:` - 代码重构
- `test:` - 测试相关
- `chore:` - 构建/工具相关

5. **推送到你的 Fork**

```bash
git push origin feature/your-feature-name
```

6. **创建 Pull Request**

## 开发环境搭建

### 后端

```bash
# 编译（跳过前端）
./mvnw clean compile -Dskip.frontend=true

# 运行测试
./mvnw test -Dmaven.test.skip=false

# 启动后端
./mvnw spring-boot:run -Dskip.frontend=true
```

### 前端

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 运行单元测试
npm run test:unit

# 类型检查
npm run build
```

## 代码规范

### Java

- 遵循 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- 使用 Spring Boot 最佳实践
- 添加必要的 JavaDoc 注释
- 单元测试覆盖核心逻辑

### TypeScript/Vue

- 使用 Composition API (`<script setup>`)
- 类型优先（避免使用 `any`）
- 组件使用 PascalCase 命名
- 遵循 Vue 3 风格指南

## 测试要求

- 新功能必须包含单元测试
- Bug 修复需要添加回归测试
- 确保所有测试通过后再提交 PR

```bash
# 后端测试
./mvnw test -Dmaven.test.skip=false

# 前端测试
cd frontend && npm run test:unit
```

## Pull Request 检查清单

提交 PR 前，请确认：

- [ ] 代码遵循项目规范
- [ ] 添加了必要的测试
- [ ] 所有测试通过
- [ ] 更新了相关文档
- [ ] 提交信息清晰明确
- [ ] 没有引入不必要的依赖
- [ ] 没有包含敏感信息（API Key、密码等）

## 行为准则

- 尊重他人，友善交流
- 欢迎新手提问
- 专注于技术讨论
- 接受建设性批评

## 许可协议

贡献的代码将采用与本项目相同的 [MIT License](../LICENSE)。

## 联系方式

如有任何问题，欢迎通过以下方式联系：

- GitHub Issues
- GitHub Discussions

感谢你的贡献！
