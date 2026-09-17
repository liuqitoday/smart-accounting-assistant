-- 最小 V1 分析执行器 fixture：手工建表，不依赖 Hibernate schema。
-- ledger 100 = 当前账本；ledger 200 = 其他账本。

CREATE TABLE categories (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    parent_id INTEGER,
    level INTEGER NOT NULL,
    type TEXT NOT NULL
);

CREATE TABLE accounts (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    ledger_id INTEGER NOT NULL
);

CREATE TABLE tags (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    is_system INTEGER NOT NULL DEFAULT 0,
    ledger_id INTEGER
);

CREATE TABLE transactions (
    id INTEGER PRIMARY KEY,
    amount NUMERIC NOT NULL,
    type TEXT NOT NULL,
    description TEXT NOT NULL,
    original_text TEXT NOT NULL,
    category_id INTEGER,
    category TEXT,
    parent_category_id INTEGER,
    parent_category_name TEXT,
    transaction_date TEXT NOT NULL,
    note TEXT,
    parsed_merchant TEXT,
    ledger_id INTEGER NOT NULL,
    account_id INTEGER,
    deleted_at TEXT
);

CREATE TABLE transaction_tags (
    transaction_id INTEGER NOT NULL,
    tag_id INTEGER NOT NULL
);

INSERT INTO categories (id, name, parent_id, level, type) VALUES
    (1, '餐饮', NULL, 1, 'EXPENSE'),
    (2, '早餐', 1, 2, 'EXPENSE'),
    (3, '工资', NULL, 1, 'INCOME'),
    (4, '生活', NULL, 1, 'EXPENSE'),
    (5, '日用品', 4, 2, 'EXPENSE'),
    (6, '交通', NULL, 1, 'EXPENSE'),
    (7, '公交', 6, 2, 'EXPENSE');

INSERT INTO accounts (id, name, type, ledger_id) VALUES
    (1, '支付宝', 'VIRTUAL', 100),
    (2, '其他账本账户', 'VIRTUAL', 200);

INSERT INTO tags (id, name, is_system, ledger_id) VALUES
    (1, '旅行', 1, NULL),
    (2, '工作', 0, 100),
    (3, '其他账本标签', 0, 200);

-- ledger 100：1 月支出、2 月仅收入（空支出月）、3 月支出；另含转账、软删除、分类回退、跨账本账户、多标签。
INSERT INTO transactions (
    id, amount, type, description, original_text,
    category_id, category, parent_category_id, parent_category_name,
    transaction_date, note, parsed_merchant, ledger_id, account_id, deleted_at
) VALUES
    (1, 10.00, 'EXPENSE', '早餐包子', '早餐包子',
     2, '早餐', 1, '餐饮',
     '2026-01-15', NULL, '包子铺', 100, 1, NULL),
    (2, 5000.00, 'INCOME', '二月工资', '二月工资',
     3, '工资', NULL, NULL,
     '2026-02-10', NULL, '公司', 100, 1, NULL),
    (3, 10.00, 'EXPENSE', '三月早餐', '三月早餐',
     2, '早餐', 1, '餐饮',
     '2026-03-20', NULL, '包子铺', 100, 1, NULL),
    (4, 50.00, 'TRANSFER', '转账到零钱', '转账到零钱',
     1, '餐饮', 1, '餐饮',
     '2026-01-20', NULL, NULL, 100, 1, NULL),
    (5, 999.00, 'EXPENSE', '已删除二月支出', '已删除二月支出',
     2, '早餐', 1, '餐饮',
     '2026-02-15', NULL, NULL, 100, 1, '2026-02-15 12:00:00'),
    (6, 20.00, 'EXPENSE', '空父分类零食', '空父分类零食',
     NULL, '零食', NULL, '',
     '2026-01-16', NULL, NULL, 100, 1, NULL),
    (7, 10.00, 'EXPENSE', '无分类支出', '无分类支出',
     NULL, '', NULL, '',
     '2026-01-17', NULL, NULL, 100, NULL, NULL),
    (8, 5.00, 'EXPENSE', '跨账本账户支出', '跨账本账户支出',
     2, '早餐', 1, '餐饮',
     '2026-03-21', NULL, NULL, 100, 2, NULL),
    (9, 10.00, 'EXPENSE', '多标签支出', '多标签支出',
     2, '早餐', 1, '餐饮',
     '2026-03-22', NULL, NULL, 100, 1, NULL),
    (10, 5.00, 'EXPENSE', '他账本标签支出', '他账本标签支出',
     2, '早餐', 1, '餐饮',
     '2026-03-23', NULL, NULL, 100, 1, NULL),
    -- ledger 200：同月支出，证明账本边界。2 月支出若泄漏会让趋势出现 2026-02。
    (11, 1000.00, 'EXPENSE', '其他账本一月', '其他账本一月',
     2, '早餐', 1, '餐饮',
     '2026-01-10', NULL, NULL, 200, 2, NULL),
    (12, 2000.00, 'EXPENSE', '其他账本二月', '其他账本二月',
     2, '早餐', 1, '餐饮',
     '2026-02-10', NULL, NULL, 200, 2, NULL),
    (13, 3000.00, 'EXPENSE', '其他账本三月', '其他账本三月',
     2, '早餐', 1, '餐饮',
     '2026-03-10', NULL, NULL, 200, 2, NULL),
    (14, 80.00, 'EXPENSE', '日用品', '日用品',
     5, '日用品', 4, '生活',
     '2026-03-25', NULL, NULL, 100, 1, NULL),
    (15, 50.00, 'EXPENSE', '公交', '公交',
     7, '公交', 6, '交通',
     '2026-03-24', NULL, NULL, 100, 1, NULL),
    (16, 3.00, 'EXPENSE', '星巴克咖啡', '星巴克咖啡',
     2, '早餐', 1, '餐饮',
     '2026-05-02', NULL, '星巴克', 100, 1, NULL),
    (17, 7.00, 'EXPENSE', '含百分号%的支出', '含百分号%的支出',
     2, '早餐', 1, '餐饮',
     '2026-05-03', NULL, '星%克', 100, 1, NULL);

INSERT INTO transaction_tags (transaction_id, tag_id) VALUES
    (9, 1),
    (9, 2),
    (10, 3);
