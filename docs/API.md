# API 文档

本文列出常用接口与统一响应格式。完整接口（含账本、账户、标签、转账、周期账单等）可通过启用 Swagger UI 查看：本地在 `application-local.yml` 中将 `springdoc.api-docs.enabled` 与 `springdoc.swagger-ui.enabled` 设为 `true`（默认关闭），随后访问 `/swagger-ui.html`。

## 认证

```http
POST /api/auth/register     # 注册
POST /api/auth/login        # 登录
GET  /api/auth/me           # 当前用户
GET  /api/auth/csrf         # 获取 CSRF 令牌
POST /api/auth/logout       # 登出
PUT  /api/auth/password     # 修改密码
```

交易接口使用登录后的 Session Cookie，并通过 `X-Ledger-Id` 指定当前账本；省略时使用用户的默认账本。写请求还需在 `X-XSRF-TOKEN` 请求头中携带与 `XSRF-TOKEN` Cookie 对应的 CSRF 令牌。下方 curl 示例假定已完成登录并将 Cookie 保存在 `cookies.txt`，账本 ID、令牌与文件名均需替换为自己的值。

## 交易

```http
GET    /api/transactions/capabilities # 功能开关与图片上传限制
POST   /api/transactions/parse-only   # AI 解析（不保存）
POST   /api/transactions/parse-image  # 图片识别（multipart，仅返回待核对记录）
POST   /api/transactions/save         # 保存解析结果
GET    /api/transactions              # 列表（分页、筛选）
GET    /api/transactions/{id}
PUT    /api/transactions/{id}
DELETE /api/transactions/{id}
POST   /api/transactions/{id}/tags    # 更新标签
GET    /api/transactions/export       # CSV 导出
POST   /api/transactions/import       # CSV 导入
```

## 截图记账

先配置支持图片输入的 OpenAI 兼容模型，并设置 `AI_MULTIMODAL_ENABLED=true`，或在本地 YAML 中设置 `app.ai.multimodal-enabled: true`，重启应用后生效。功能默认关闭，复用当前 AI 的 base URL、API key 和模型；凭据不会通过能力接口返回。

### 1. 查询能力与上传限制

```bash
curl 'http://localhost:8081/api/transactions/capabilities' \
  -b cookies.txt \
  -H 'X-Ledger-Id: 1'
```

响应示例（默认关闭）：

```json
{
  "success": true,
  "data": {
    "imageParsingEnabled": false,
    "maxImageSizeBytes": 5242880,
    "supportedImageTypes": ["image/png", "image/jpeg"],
    "maxTransactionsPerImage": 20
  },
  "message": "操作成功",
  "errorCode": null
}
```

客户端仅在 `imageParsingEnabled=true` 时展示截图入口；查询失败时也保持隐藏。该接口要求登录并拥有当前账本的查看权限；识别与保存要求账本 `OWNER` 或 `EDITOR` 权限，`VIEWER` 不能调用。

### 2. 上传截图并识别

请求为 `multipart/form-data`，必填字段名为 **`file`**，一次上传一张图片。使用 curl `-F` 时由 curl 自动生成包含 boundary 的 `Content-Type`，不要手动指定为 JSON。

```bash
curl 'http://localhost:8081/api/transactions/parse-image' \
  -b cookies.txt \
  -H 'X-Ledger-Id: 1' \
  -H 'X-XSRF-TOKEN: your-csrf-token' \
  -F 'file=@receipt.png;type=image/png'
```

上传限制为 PNG/JPEG、单张最多 **5 MiB（5,242,880 字节）**、最多 **20,000,000 像素**，一张截图最多识别 **20 笔**。后端校验实际图片内容、完整性、尺寸与声明类型，修改扩展名不能把其它文件变为可接受的图片。反向代理的请求体上限应至少为 6 MiB，以容纳 multipart 边界。

成功响应示例（截图没有完整日期，需要用户补充）：

```json
{
  "success": true,
  "data": {
    "transactions": [
      {
        "id": null,
        "amount": 35.00,
        "type": "EXPENSE",
        "description": "购买咖啡",
        "originalText": "咖啡店 实付35.00元",
        "categoryId": 123,
        "categoryName": "咖啡",
        "transactionDate": null,
        "parsedMerchant": "咖啡店",
        "confidenceScore": 0.95,
        "aiModelUsed": "your-vision-model",
        "aiSuggestedCategoryId": 123,
        "learnedFromHistory": false,
        "fallbackUsed": false,
        "accountId": null,
        "note": null,
        "relatedUser": null
      }
    ],
    "warnings": ["第 1 笔未识别到完整日期，请补充后保存"]
  },
  "message": "截图识别完成，请核对后保存",
  "errorCode": null
}
```

`transactions` 是待核对记录数组，识别时不写入交易；`warnings` 提示缺失、不确定或跳过的内容。客户端应展示提示并允许逐笔修改或跳过。金额是正数，`type` 仅支持 `EXPENSE` / `INCOME`；缺少完整日期时 `transactionDate=null`，无法匹配分类时 `categoryId=null`，这些字段需补齐后才能保存。示例分类 ID 仅用于说明，实际应使用返回的有效分类或从 `/api/categories` 选择。

分类匹配会参考已有分类和当前账本的历史纠正记录；`learnedFromHistory` 表示命中历史习惯。账户仅在当前账本启用的账户中按截图明确出现的名称唯一匹配，未匹配或存在歧义时 `accountId=null`，不会猜测默认账户。`originalText` 保存该笔交易相关的识别文字，原图不会作为附件或数据库记录持久保存。图片会发送给当前配置的 AI 服务进行识别。

模型会排除余额、合计、商品分项重复计账、未支付或取消订单、本人账户互转和明确的非人民币交易；服务端进一步校验金额、类型、币种等字段。没有可用交易时返回错误，模型失败时也不会用文本规则猜测图片内容。识别结果仍需要用户对照原图确认。

### 3. 逐笔确认后保存

确认每条记录后，调用已有的 `POST /api/transactions/save`。沿用当前账本、Session 与 CSRF 信息，并保留返回的 `originalText`、`aiSuggestedCategoryId` 等解析信息；用户可以修改金额、日期、类型、分类、描述、商户、备注和账户。下面演示补齐日期后保存一笔：

```bash
curl 'http://localhost:8081/api/transactions/save' \
  -b cookies.txt \
  -H 'X-Ledger-Id: 1' \
  -H 'X-XSRF-TOKEN: your-csrf-token' \
  -H 'Content-Type: application/json' \
  --data '{
    "amount": 35.00,
    "type": "EXPENSE",
    "description": "购买咖啡",
    "originalText": "咖啡店 实付35.00元",
    "categoryId": 123,
    "transactionDate": "2026-11-09",
    "parsedMerchant": "咖啡店",
    "confidenceScore": 0.95,
    "aiModelUsed": "your-vision-model",
    "aiSuggestedCategoryId": 123,
    "accountId": null
  }'
```

保存成功仍返回 `ApiResponse<TransactionParseResponse>`，其中 `data.id` 为新交易 ID，`message` 为“交易记录保存成功”。每次调用只保存一笔；客户端应标记已保存项，避免把同一项再次提交。重新上传相同截图不会自动去重，应核对是否已经记账。

### 配额与错误

图片与文字解析共用 `app.ai.daily-limit`，默认每用户每天 200 次，按 `Asia/Shanghai` 日期重置。一张通过图片校验并进入识别流程的截图消耗一次额度，与其中笔数无关；模型调用失败仍计次。功能关闭或图片校验未通过时不消耗识别额度。逐笔保存不消耗 AI 解析额度。

应用返回的错误使用真实 HTTP 状态与相同响应结构，例如：

```json
{
  "success": false,
  "data": null,
  "message": "当前未开启图片记账，请使用文字或手动记账",
  "errorCode": "IMAGE_PARSING_DISABLED"
}
```

| HTTP 状态 | `errorCode` | 含义 |
|---|---|---|
| 400 | `IMAGE_PARSING_DISABLED` | 当前配置未启用截图记账 |
| 400 | `INVALID_IMAGE` | 空文件、不支持的格式、类型不符或图片已损坏 |
| 400 | `IMAGE_TOO_LARGE` | 图片业务校验发现超过 5 MiB |
| 400 | `IMAGE_DIMENSIONS_EXCEEDED` | 超过 2000 万像素 |
| 400 | `IMAGE_TOO_MANY_TRANSACTIONS` | 超过 20 笔，请裁剪后分次识别 |
| 400 | `IMAGE_PARSE_FAILED` | 模型不支持图片、调用失败或返回格式无效 |
| 400 | `NO_TRANSACTIONS_FOUND` | 没有可记账的人民币消费或收入 |
| 400 | `INVALID_UPLOAD` | 缺少 `file` 字段或 multipart 格式无效 |
| 400 | `null` | 当日 AI 配额耗尽，具体原因见 `message` |
| 401 / 403 | 以响应为准 | 未登录、缺少 CSRF 令牌或没有当前账本的编辑权限 |
| 413 | `UPLOAD_TOO_LARGE` | 上传被应用 multipart 文件/请求大小限制拒绝 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 请求没有使用要求的 multipart 格式 |

## 分析

```http
POST   /api/analysis/chat             # AI 分析对话
GET    /api/analysis/messages         # 聊天历史
DELETE /api/analysis/messages         # 清空历史
```

## 统计

```http
GET /api/statistics/summary           # 汇总（含环比）
GET /api/statistics/by-category       # 分类构成
GET /api/statistics/trend             # 月度趋势
GET /api/statistics/trend/daily       # 日趋势
GET /api/statistics/top-expenses      # 大额支出排行
GET /api/statistics/recent            # 最近交易
```

## 响应格式

JSON 接口统一使用 `ApiResponse<T>`；CSV 导出返回文件内容，不使用此 JSON 包装。错误请求同样使用包装结构，并返回真实的 400/401/403/404/413/415/500 等 HTTP 状态。反向代理直接拒绝的请求，其响应格式由代理决定。

成功响应示例：

```json
{
  "success": true,
  "data": {},
  "message": "操作成功",
  "errorCode": null
}
```
