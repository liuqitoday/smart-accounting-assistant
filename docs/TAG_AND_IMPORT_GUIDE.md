# 标签与 CSV 导入指南

## 1. 标签系统

每个用户可以创建自己的标签，系统也会预置少量通用标签。交易 ↔ 标签是多对多关系，单个交易可挂多个标签。

### 系统预置标签

- 由 `SystemTagInitializer` 在应用启动时幂等注入（`CommandLineRunner`）
- `created_by` 字段固定为哨兵值 `__system__`（非真实用户，注册时该用户名被禁用）
- 任何用户都可见、可用，但不可修改、不可删除
- 当前预置：`旅行 (#14B8A6)`

### 用户标签

- 同一用户名下标签名唯一
- 不能与系统预置标签重名
- 只能由创建者修改/删除

## 2. 标签管理 API

| 方法 | 端点 | 说明 |
|------|------|------|
| GET    | `/api/tags`                        | 获取当前用户的标签 + 所有系统标签 |
| POST   | `/api/tags`                        | 创建用户标签 |
| PUT    | `/api/tags/{id}`                   | 修改用户标签（系统标签 403） |
| DELETE | `/api/tags/{id}`                   | 删除用户标签（系统标签 403） |

### 创建标签

```http
POST /api/tags
Content-Type: application/json
Authorization: Bearer <token>

{ "name": "餐饮", "color": "#F97316" }
```

- `name` 必填，最长 50 字符
- `color` 可选，建议使用前端预设的 10 种调色板之一

### 给交易打标签

```http
POST /api/transactions/{id}/tags
Content-Type: application/json
Authorization: Bearer <token>

{ "tagIds": [1, 2, 3] }
```

- 系统标签可直接引用；用户标签必须属于当前用户
- 整组覆盖：传空数组 `[]` 等价于清空标签

## 3. 与筛选的配合

`GET /api/transactions?tagIds=1,2,3` 即可按标签过滤，详见 [FILTER_AND_EXPORT_GUIDE.md](./FILTER_AND_EXPORT_GUIDE.md)。

- 多标签为 OR 语义（命中任一即返回）
- 筛选 + 分页在数据库层一次性完成，分页准确

## 4. CSV 导入

`POST /api/transactions/import`，`multipart/form-data`，字段名 `file`。

### 行为

- **无 ID 行** → 以当前登录用户身份新增
- **有 ID 行** → 更新对应记录（ID 必须存在且属于当前用户）
- 单行失败不阻断整批导入，错误在响应中逐行列出
- 标签列留空 `""` 表示清空该交易的标签；缺列则保留原标签

### 字段规则

| 字段 | 必填 | 说明 |
|------|------|------|
| `ID` | 否 | 留空新增；填写则按 ID 更新（必须存在且属于当前用户） |
| `交易日期` | 是 | `yyyy-MM-dd` 或 `yyyy-MM-dd HH:mm:ss` |
| `类型` | 是 | `INCOME` / `EXPENSE` |
| `金额` | 是 | 数字 |
| `描述` | 是 | 简短描述 |
| `原始文本` | 是 | 原始自然语言文本 |
| `创建人` | 是 | 必须与当前登录用户名一致 |
| `分类` / `父分类` | 新增时必填 | 按名称匹配；未匹配则保留原分类（仅警告） |
| `标签` | 否 | 逗号分隔，不存在的用户标签自动创建；系统标签按名匹配 |

### 响应

```json
{
  "success": true,
  "data": {
    "totalRows": 100,
    "createdCount": 10,
    "updatedCount": 85,
    "errorCount": 5,
    "errors": [
      { "row": 12, "field": "类型", "message": "类型不能为空" }
    ]
  }
}
```

## 5. 典型工作流：导出 → 打标签 → 导入

```bash
BASE="http://localhost:8081"
TOKEN="<your-token>"

# 1) 创建用户标签
curl -X POST $BASE/api/tags \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"已导入随手记","color":"#10B981"}'

# 2) 导出所有交易
curl -o transactions.csv $BASE/api/transactions/export \
  -H "Authorization: Bearer $TOKEN"

# 3) 在 Excel/Vim 中编辑：在「标签」列追加 "已导入随手记,2024年报税"

# 4) 导入回系统
curl -X POST $BASE/api/transactions/import \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@transactions.csv"
```

## 6. 常见问题

**Q: 标签筛选是 AND 还是 OR？**  
A: 当前为 OR（`tagIds=1,2,3` 返回含任一标签的记录）。

**Q: 创建标签提示「是系统预置标签」？**  
A: 你创建的名字和某个系统标签重名了，换个名字即可。

**Q: 删除标签后交易会被删除吗？**  
A: 不会。删除标签只会解除关联关系（多对多中间表），交易本身保留。

**Q: CSV 导入时报「ID xxx 不存在」？**  
A: 有 ID 的行必须能在数据库中找到，且属于当前登录用户；找不到会报整行错误。
