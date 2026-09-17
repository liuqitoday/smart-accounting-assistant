# 交易记录筛选、导出与导入

## 1. 功能一览

| 功能 | 端点 | 说明 |
|------|------|------|
| 查询（带筛选+分页） | `GET /api/transactions` | 支持类型、日期范围、标签任意组合 |
| 导出 CSV（带筛选） | `GET /api/transactions/export` | 不分页，返回所有匹配结果 |
| 导入 CSV | `POST /api/transactions/import` | multipart/form-data，ID 留空新增 / 填写 ID 更新 |

筛选条件在数据库层通过 JPA Specification 一次性完成，不存在内存二次筛选，分页准确。

## 2. 筛选参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `type` | String | 否 | `INCOME` 或 `EXPENSE` |
| `startDate` | LocalDate | 否 | 起始日期（含），单独使用表示「该日期之后」 |
| `endDate` | LocalDate | 否 | 结束日期（含），单独使用表示「该日期之前」 |
| `tagIds` | List<Long> | 否 | 标签 ID 列表，多个值用 `,` 分隔；语义为 OR（包含任一） |
| `page` | Integer | 否 | 页码（从 0 开始），仅查询接口 |
| `size` | Integer | 否 | 每页数量（默认 20），仅查询接口 |
| `sort` | String | 否 | 形如 `transactionDate,desc`，仅查询接口 |

多条件之间为 AND 关系，全部在数据库 WHERE 条件中完成。

## 3. 常用示例

```bash
BASE="http://localhost:8081/api"

# 仅按类型
curl "$BASE/transactions?type=EXPENSE"

# 日期范围（两端都指定）
curl "$BASE/transactions?startDate=2024-01-01&endDate=2024-12-31"

# 起始日期之后（单边范围，现在已支持）
curl "$BASE/transactions?startDate=2024-06-01"

# 按标签（OR 语义：含任一即匹配）
curl "$BASE/transactions?tagIds=1,2,3"

# 组合筛选
curl "$BASE/transactions?type=EXPENSE&startDate=2024-03-01&endDate=2024-03-31&tagIds=5"

# 按日期排序 + 分页
curl "$BASE/transactions?sort=transactionDate,desc&page=0&size=20"
```

## 4. 导出

`GET /api/transactions/export` 与查询接口共用筛选参数（无 `page` / `size` / `sort`）。

```bash
# 导出所有
curl -o all.csv "$BASE/transactions/export"

# 导出 2024 年所有收入
curl -o income_2024.csv "$BASE/transactions/export?type=INCOME&startDate=2024-01-01&endDate=2024-12-31"

# 按标签导出
curl -o project_a.csv "$BASE/transactions/export?tagIds=10"
```

**CSV 格式**（UTF-8 with BOM，Excel 友好）：

```
ID,交易日期,类型,金额,描述,原始文本,分类,父分类,商户,备注,相关人员,置信度,AI模型,创建人,创建时间,更新时间,标签
123,2024-03-15,EXPENSE,35.00,星巴克咖啡,今天在星巴克买了咖啡 35 元,餐饮,日常支出,星巴克,,,,gpt-4o-mini,user1,2024-03-15 10:30:00,2024-03-15 10:30:00,"早餐,咖啡"
```

- 「标签」列用英文逗号分隔多个标签
- 包含逗号 / 引号 / 换行的字段按 RFC 4180 用双引号包裹并转义

## 5. 导入

`POST /api/transactions/import`，`multipart/form-data`，字段名 `file`。

### 行为

- **无 ID 行** → 以当前登录用户身份新增
- **有 ID 行** → 更新对应记录（ID 必须存在且属于当前用户）
- 单行失败不阻断整批导入，错误信息在响应中逐行列出

### 字段规则

| 字段 | 必填 | 说明 |
|------|------|------|
| `ID` | 否 | 留空新增；填写则按 ID 更新 |
| `交易日期` | 是 | `yyyy-MM-dd` 或 `yyyy-MM-dd HH:mm:ss` |
| `类型` | 是 | `INCOME` / `EXPENSE` |
| `金额` | 是 | 数字 |
| `描述` | 是 | 简短描述 |
| `原始文本` | 是 | 原始自然语言文本 |
| `创建人` | 是 | 必须与当前登录用户名一致 |
| `分类` / `父分类` | 新增时必填 | 按名称匹配当前用户所属分类 |
| `标签` | 否 | 逗号分隔，不存在则自动创建 |

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
      { "row": 12, "field": "分类", "message": "未找到分类" }
    ]
  }
}
```

## 6. 常见问题

**Q: 标签筛选是 AND 还是 OR？**  
A: 当前为 OR（`tagIds=1,2,3` 返回含任一标签的记录）。

**Q: 导出 CSV 在 Excel 里乱码？**  
A: 文件已带 UTF-8 BOM，Excel 2016+ 通常自动识别。如仍乱码，用「数据 → 从文本/CSV」并选 UTF-8。

**Q: 大量数据导出慢？**  
A: 建议按月/季度分批导出，控制在 1 万条以内。

**Q: 导入时 ID 找不到？**  
A: 必须是当前用户已存在的交易 ID，且与 `创建人` 列匹配。跨用户 ID 会被拒绝。
