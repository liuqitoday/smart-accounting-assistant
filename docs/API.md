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

## 交易

```http
POST   /api/transactions/parse-only   # AI 解析（不保存）
POST   /api/transactions/save         # 保存解析结果
GET    /api/transactions              # 列表（分页、筛选）
GET    /api/transactions/{id}
PUT    /api/transactions/{id}
DELETE /api/transactions/{id}
POST   /api/transactions/{id}/tags    # 更新标签
GET    /api/transactions/export       # CSV 导出
POST   /api/transactions/import       # CSV 导入
```

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

所有响应格式：

```json
{
  "success": true,
  "data": { ... },
  "message": "成功",
  "errorCode": null
}
```
