# MCP 接入指南

记账助手提供通用 MCP 服务，支持 Agent 记账、查询明细和统计。网页与 Agent 使用同一份账目和账本权限。当前提供 Streamable HTTP 和本地 stdio 桥接，认证使用专用 Bearer Token；OAuth 浏览器授权尚未提供。

## 创建连接

在桌面侧栏「管理 → MCP 连接」或手机「我的 → MCP 连接」中：

1. 创建连接，填写一个便于识别的名称。
2. 显式选择可访问账本；默认账本可选，未设置时由 Agent 向用户确认。
3. 选择「只读查询」或「记账与查询」，有效期可选 30、90、365 天。
4. 保存服务地址和只显示一次的完整令牌。

每个 Agent 建议使用不同连接。连接列表可以查看最近 100 次调用、更换令牌或撤销连接。更换令牌立即作废原令牌，保持原有效期。修改登录密码会撤销该用户所有 MCP 令牌。令牌摘要保存在服务器，完整令牌不支持找回。

有效权限同时受连接选定账本、操作权限及用户当前账本角色限制。VIEWER 不能记账；被移出账本后不能继续查询。删除账本会清除对应授权，后续新账本不会继承它。固定令牌代表其所属的记账用户，多人共用 Agent 时不能把这份凭据当作每个聊天用户的独立身份。

## 远程 Streamable HTTP

在客户端的 MCP 设置中填写：

| 项目 | 值 |
| --- | --- |
| 名称 | accounting（可自定义） |
| URL | `https://your-domain.com/mcp` |
| 传输 | Streamable HTTP |
| 认证 | Bearer Token |
| 请求头（客户端没有认证选择时） | `Authorization: Bearer <专用令牌>` |

各客户端的配置文件格式不同，上表是通用连接信息。无需安装某个 Agent 厂商的专有插件或 Skill。客户端必须支持手工配置 Bearer 凭据；只支持 OAuth 的客户端目前不能直接连接。

生产部署必须将实际域名加入 `MCP_ALLOWED_HOSTS`。它是逗号分隔的主机名列表，不包含 scheme 或端口；默认仅允许 localhost、127.0.0.1 和 [::1]。例如 Docker Compose 的 `.env`：

```dotenv
MCP_ENABLED=true
MCP_ALLOWED_HOSTS=accounting.example.com,localhost,127.0.0.1,[::1]
```

修改后重新创建容器。直接运行 jar 时使用同名环境变量；本地 `application-local.yml` 也可配置：

```yaml
app:
  mcp:
    allowed-hosts: accounting.example.com,localhost,127.0.0.1,[::1]
```

使用 HTTPS 对外提供服务，反向代理需保留 `Authorization`、Host、协议转发头和 MCP 请求头。仅让可信代理访问后端端口，避免客户端自行伪造转发头。现有 Nginx 部署配置可代理 `/mcp`，请求体上限至少 64 KiB。设置 `MCP_ENABLED=false` 时 MCP 入口返回 404。

请求有 Origin 时，只接受同源或 `MCP_ALLOWED_ORIGINS` 中的精确来源（含 scheme 和端口）。当前不提供跨站浏览器 CORS 接入，推荐客户端的本地 / 服务端 MCP 连接器。普通 CLI 和桌面连接器无需设置 Origin。不能通过网页 Cookie 访问 MCP，也不能用 MCP 令牌管理网页连接设置。

## 本地 stdio 桥接

只支持启动本地进程的客户端可以使用仓库中的 [bridge.mjs](../mcp/bridge.mjs)，需要 Node.js 22+，无需 npm 安装依赖。桥接连接上述远程入口，账目仍保存在记账服务中。

以下为常见 `mcpServers` 格式的示例；根据客户端调整配置文件的外层结构。脚本路径必须改为运行 Agent 那台机器上的绝对路径，令牌填入客户端环境变量 / 密钥设置。

```json
{
  "mcpServers": {
    "accounting": {
      "command": "node",
      "args": ["/absolute/path/to/accounting-assistant-opensource/mcp/bridge.mjs"],
      "env": {
        "ACCOUNTING_MCP_URL": "https://your-domain.com/mcp",
        "ACCOUNTING_MCP_TOKEN": "<专用令牌>"
      }
    }
  }
}
```

桥接仅接受 HTTPS 或 loopback HTTP，不跟随重定向，不自动重试写入；stdout 只输出 MCP 消息。它是本服务无状态 JSON 响应的适配器，不是任意远程 MCP/SSE 服务的通用代理。服务端协议版本由初始化协商，桥接将协商结果用于后续 HTTP 请求。

## 六个工具

| 工具 | 用途 |
| --- | --- |
| `accounting_get_context` | 服务端上海日期、币种、授权账本、有效权限和默认账本 |
| `accounting_get_ledger_options` | 分类树、启用 / 停用账户选项和标签，不返回账户余额 |
| `accounting_create_transaction` | 新增一笔已经发生的人民币收入或支出 |
| `accounting_list_transactions` | 指定日期和筛选条件查询分页明细 |
| `accounting_get_transaction` | 指定账本内的账目详情，软删除记录返回 NOT_FOUND |
| `accounting_get_statistics` | summary 汇总、by_category 分类统计、trend 日 / 月趋势 |

每个账本工具显式传 `ledgerId`，不读取网页当前账本。金额为十进制字符串，最多 13 位整数、2 位小数；创建金额必须大于零。日期严格使用 `YYYY-MM-DD`、Asia/Shanghai，统计起止日期均包含边界。创建必须提供类别，账户可省略，省略表示未指定账户；不能使用停用账户或其他账本的账户、标签。

列表和统计共享关键词范围：描述、原文和备注；商户使用 `merchant` 参数。标签按任意一个匹配。分类匹配分类自身或其子分类。无效引用、未知参数和不支持的筛选条件报错，不会静默丢弃。

列表页码从 0 开始，默认 20 条、最多 100 条；按日期、ID 降序，返回总笔数和 `hasMore`。未指定 type 时列表包含账户转账，统计仅计普通收入和支出，排除软删除和转账。总金额应从统计工具读取，不能将一页结果相加当作全部金额。分类统计需要指定 INCOME 或 EXPENSE，最多显示 50 个分类，返回全量总金额及截断提示；趋势最多 366 个时间段。

创建示例（账本、分类、账户 ID 请通过工具获取）：

```json
{
  "ledgerId": 1,
  "type": "EXPENSE",
  "amount": "35.00",
  "currency": "CNY",
  "transactionDate": "2026-11-20",
  "description": "午饭",
  "categoryId": 12,
  "accountId": 3,
  "clientRequestId": "0ac198ce-b2b1-4cd9-9bc7-bf2f4dbaf02e"
}
```

每个新记账意图创建稳定的 UUID，超时或响应丢失后必须以同一 UUID、完全相同的参数重试。服务端按用户、账本、UUID 保存持久回执；重复调用返回原回执，不重复入账；同一 UUID 对应不同内容返回 `IDEMPOTENCY_CONFLICT`。重新生成 UUID 的另一次请求不具备内容去重保证。

成功结果同时提供 `structuredContent` 和 JSON 文本 `content`，包含 `ok=true` 和 `data`。业务失败使用 `isError=true`，返回 `ok=false`、`errorCode`、`message` 和 `retryable`。工具错误与成功空列表不同。创建工具成功返回的是创建时回执，后续编辑或删除后的现状通过详情查询确认。

记账及统计工具不会额外调用内部大模型。创建来源与账目、标签、去重回执在同一事务中写入，来源长期保留；普通成功 / 失败调用元数据保留 90 天，不保存完整对话和返回账单。每用户每分钟最多 120 个 MCP HTTP 请求、最多 4 个并发；超限返回 HTTP 429 / Retry-After。请求上限 64 KiB，工具结果上限 1 MiB，超大查询需缩小范围。

## 联调与验证

当前依赖为 Spring AI 1.1.8 / MCP Java SDK 0.18.3，实际验证了 `2025-11-25` 初始化协商。支持不同客户端的通用协议能力，未逐个认证所有 Agent 产品。需要仅支持更新协议修订的客户端时，应先核对共同版本。

开发检查：

```bash
./mvnw -Dskip.frontend=true -Dmaven.test.skip=false -DskipTests=false test
cd frontend
npm run test:unit -- --run
npm run build
```

在仓库根目录验证桥接：

```bash
node --test mcp/bridge.test.mjs
```

可用 MCP Inspector 或 Postman CLI 检查协议。Postman CLI 需提供 `postman mcp` 命令的版本（联调使用 1.92.1）：

```bash
postman mcp tools list --url "$ACCOUNTING_MCP_URL" \
  -H "Authorization: Bearer $ACCOUNTING_MCP_TOKEN" --no-login
postman mcp tools call accounting_get_context --args '{}' \
  --url "$ACCOUNTING_MCP_URL" -H "Authorization: Bearer $ACCOUNTING_MCP_TOKEN" --no-login
```

如客户端仅允许新版协议，选择其兼容模式并核对服务支持的协议版本。401 通常表示令牌无效、过期或撤销；403 的 HOST_NOT_ALLOWED 表示未配置域名，ORIGIN_NOT_ALLOWED 表示来源不受信任；工具 FORBIDDEN 表示账本或操作未授权；仅能列出工具并不代表有写入权限。

OAuth、修改 / 删除 / 恢复账目、转账写入、截图识别和周期账单不在本版 MCP 工具范围内，网页原有能力可继续使用。
