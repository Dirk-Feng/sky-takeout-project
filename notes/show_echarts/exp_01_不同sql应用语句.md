# MySQL 常用统计 SQL 速查笔记

> TASK07 — 苍穹外卖项目
>
> 用途：记录后台统计报表中常见的 SQL 组合及其业务应用场景。

## 1. SUM + GROUP BY：分组求和

**适用场景：**

- 每日营业额统计
- 每个商品的销售额
- 每个用户的消费总额
- 按月份、分类、地区进行金额汇总

**示例：统计每天的营业额**

```sql
SELECT
    DATE(order_time) AS date,
    SUM(amount) AS turnover
FROM orders
WHERE status = 5
  AND order_time >= #{beginTime}
  AND order_time < #{endTimeExclusive}
GROUP BY DATE(order_time)
ORDER BY DATE(order_time);
```

**核心语法：**

- `SUM()`：求和。
- `GROUP BY`：按照指定字段分组。
- `DATE()`：提取日期，忽略时分秒。
- `ORDER BY`：对查询结果排序。

**记忆：** 需要按照某个维度对数据进行分类汇总时，使用 `GROUP BY + SUM`。

---

## 2. COUNT + GROUP BY：分组计数

**适用场景：**

- 每日新增用户数
- 每日订单数量
- 每个分类下的商品数量
- 每个用户的订单数量

**示例：统计每天新增用户数量**

```sql
SELECT
    DATE(create_time) AS date,
    COUNT(*) AS new_users
FROM user
WHERE create_time >= #{beginTime}
  AND create_time < #{endTimeExclusive}
GROUP BY DATE(create_time)
ORDER BY DATE(create_time);
```

注意：`create_time` 需要替换为项目实际使用的用户注册时间字段。

**核心语法：**

- `COUNT(*)`：统计符合条件的记录数量。
- `GROUP BY`：按日期分组。

**记忆：** 需要统计每个分组里面有多少条记录，使用 `COUNT + GROUP BY`。

---

## 3. CASE WHEN：条件判断与条件聚合

**适用场景：**

- 每日总订单数与有效订单数
- 已完成订单与取消订单数量
- 不同状态的订单分别统计
- 按条件统计金额

**示例：同时统计每日订单总数和有效订单数**

```sql
SELECT
    DATE(order_time) AS date,
    COUNT(*) AS total_orders,
    SUM(
        CASE
            WHEN status = 5 THEN 1
            ELSE 0
        END
    ) AS valid_orders
FROM orders
WHERE order_time >= #{beginTime}
  AND order_time < #{endTimeExclusive}
GROUP BY DATE(order_time);
```

这里的 `status = 5` 仅为示例，实际需要按照项目的有效订单定义确定。

**理解 CASE WHEN：**

```sql
CASE
    WHEN 条件 THEN 1
    ELSE 0
END
```

符合条件返回 1，否则返回 0。

再通过 `SUM()` 累加，就可以统计符合条件的记录数量。

也可以统计金额：

```sql
SUM(
    CASE
        WHEN status = 5 THEN amount
        ELSE 0
    END
)
```

**记忆：** 同一批数据需要按照不同条件统计多个指标时，优先考虑 `CASE WHEN + SUM`。

---

## 4. JOIN + GROUP BY：多表关联统计

**适用场景：**

- 商品销量统计
- 用户消费统计
- 订单明细汇总
- 需要同时使用主表和附表字段的统计

**示例：统计商品销量 Top10**

```sql
SELECT
    od.name,
    SUM(od.number) AS sales
FROM order_detail od
INNER JOIN orders o
    ON od.order_id = o.id
WHERE o.order_time >= #{beginTime}
  AND o.order_time < #{endTimeExclusive}
  AND o.status = #{validStatus}
GROUP BY od.name
ORDER BY sales DESC
LIMIT 10;
```

**核心语法：**

- `JOIN`：关联两张表。
- `ON`：指定关联条件。
- `SUM(od.number)`：累计商品购买数量。
- `GROUP BY`：按照商品分组。
- `ORDER BY sales DESC`：按照销量降序排列。
- `LIMIT 10`：只取前十条。

**记忆：** 当需要统计的数据分散在多张表中，使用 JOIN 关联后再进行聚合。

注意：按商品名称分组可能合并不同的同名商品。真实业务中更适合按照商品 ID 和类型分组。

---

## 5. ORDER BY + LIMIT：排序与 TopN

**适用场景：**

- 商品销量排行榜
- 用户消费排行榜
- 热门商品排行
- 最近创建的订单
- 最新记录查询

**示例：取销量最高的前十名**

```sql
ORDER BY sales DESC
LIMIT 10;
```

其他常见写法：

```sql
-- 取前 5 条
LIMIT 5;

-- 从第 10 条开始，取 10 条
LIMIT 10, 10;
```

**记忆：** 排名、TopN、分页，通常需要 `ORDER BY + LIMIT`。

---

## 6. 前缀和：累计统计

**适用场景：**

- 每日累计用户数
- 每日累计订单数
- 每日累计销售额
- 累计库存变化

假设需要查询 11-12 至 11-15 的累计用户数。

累计用户数的计算逻辑：

\[
Total_i=Users_{before}+\sum_{j=begin}^{i}NewUsers_j
\]

推荐采用两次 SQL + Java 前缀和。

**SQL 1：查询每日新增用户**

```sql
SELECT
    DATE(create_time) AS date,
    COUNT(*) AS new_users
FROM user
WHERE create_time >= #{beginTime}
  AND create_time < #{endTimeExclusive}
GROUP BY DATE(create_time);
```

**SQL 2：查询开始日期之前的用户总数**

```sql
SELECT COUNT(*)
FROM user
WHERE create_time < #{beginTime};
```

**Java：计算前缀和**

```java
long total = totalBefore;

for (LocalDate date : dateList) {
    long daily = dailyMap.getOrDefault(date, 0L);
    total += daily;

    // daily：当天新增用户数
    // total：截至当天的累计用户数
}
```

另一种方式是使用 SQL 窗口函数：

```sql
SUM(new_users) OVER (
    ORDER BY stat_date
    ROWS BETWEEN UNBOUNDED PRECEDING
    AND CURRENT ROW
)
```

它可以直接在查询结果中计算累计和。

**记忆：** 累计值 = 初始值 + 每日新增值的前缀和。

---

## 7. 日期范围：左闭右开

这是统计 SQL 中非常重要的一个习惯。

推荐：

```sql
WHERE order_time >= #{beginTime}
  AND order_time < #{endTimeExclusive}
```

假设用户查询：

2026-11-15 至 2026-11-24（包含首尾）。

Java 传入：

```java
beginTime = 2026-11-15T00:00:00;
endTimeExclusive = 2026-11-25T00:00:00;
```

这样就能完整覆盖 11 月 24 日。

**为什么不使用 BETWEEN？**

因为 BETWEEN 两端通常都是包含的，使用左闭右开更容易处理连续日期区间，也能避免时间精度带来的边界问题。

---

## 8. COALESCE：处理 NULL

**适用场景：**

- 没有订单时将营业额设置为 0
- 没有统计结果时补默认值
- 聚合查询结果为空时处理空值

示例：

```sql
SELECT COALESCE(SUM(amount), 0)
FROM orders
WHERE status = 5;
```

含义：

如果 `SUM(amount)` 返回 NULL，就返回 0。

注意：`GROUP BY` 不会自动生成没有数据的日期，需要 Java 补齐日期，或者通过日期表/CTE 生成完整日期序列。

---

## 9. 统计报表的 SQL 组合速查表

| 业务需求 | 推荐 SQL |
|---|---|
| 每日营业额 | `SUM + GROUP BY` |
| 每日新增用户 | `COUNT + GROUP BY` |
| 每日订单总量 | `COUNT + GROUP BY` |
| 每日有效订单量 | `CASE WHEN + SUM` |
| 每日多个条件指标 | `CASE WHEN + GROUP BY` |
| 商品销量 Top10 | `JOIN + SUM + GROUP BY + ORDER BY + LIMIT` |
| 累计用户量 | `COUNT + Java 前缀和` |
| 累计值进阶计算 | `SUM() OVER()` |
| 空值处理 | `COALESCE` |
| 日期范围过滤 | 左闭右开 |

---

## 10. 后端统计开发的几个经验

### ① 能在数据库一次完成的聚合，尽量不要逐条查询

不推荐：

```java
for (LocalDate date : dateList) {
    // 每天查询一次数据库
}
```

推荐：

```text
SQL 一次 GROUP BY
        ↓
Java 获取结果
        ↓
Map 转换
        ↓
补齐没有数据的日期
        ↓
组装 VO
```

### ② 数据库负责数据聚合，Java 负责业务组织

适合数据库：
- SUM
- COUNT
- GROUP BY
- JOIN
- 排序
- 条件聚合

适合 Java：
- 生成完整日期列表
- 补齐缺失日期
- 计算简单前缀和
- 组装 VO

### ③ 一条 SQL 不一定永远优于两条 SQL

不要为了减少 SQL 数量而强行写复杂查询。

例如累计用户统计，采用两次 SQL + Java 前缀和，比复杂的递归 CTE 更容易维护。

### ④ 注意数据库索引

日期范围过滤尽量直接作用于原始时间字段：

```sql
WHERE order_time >= ?
  AND order_time < ?
```

不要轻易写成：

```sql
WHERE DATE(order_time) = ?
```

后者可能使普通时间索引无法有效用于范围检索。

具体执行计划可以使用 `EXPLAIN` 检查。

---

**本篇核心总结：**

遇到统计需求时，先思考四件事：

1. 数据在哪张表？是否需要 JOIN？
2. 按什么维度统计？是否需要 GROUP BY？
3. 统计什么指标？SUM、COUNT 还是 CASE WHEN？
4. 是否需要排序、TopN 或累计计算？

掌握这几类 SQL 组合，就能解决相当多的后台管理统计需求。# MySQL 常用统计 SQL 速查笔记

> TASK07 — 苍穹外卖项目
>
> 用途：记录后台统计报表中常见的 SQL 组合及其业务应用场景。

## 1. SUM + GROUP BY：分组求和

**适用场景：**

- 每日营业额统计
- 每个商品的销售额
- 每个用户的消费总额
- 按月份、分类、地区进行金额汇总

**示例：统计每天的营业额**

```sql
SELECT
    DATE(order_time) AS date,
    SUM(amount) AS turnover
FROM orders
WHERE status = 5
  AND order_time >= #{beginTime}
  AND order_time < #{endTimeExclusive}
GROUP BY DATE(order_time)
ORDER BY DATE(order_time);
```

**核心语法：**

- `SUM()`：求和。
- `GROUP BY`：按照指定字段分组。
- `DATE()`：提取日期，忽略时分秒。
- `ORDER BY`：对查询结果排序。

**记忆：** 需要按照某个维度对数据进行分类汇总时，使用 `GROUP BY + SUM`。

---

## 2. COUNT + GROUP BY：分组计数

**适用场景：**

- 每日新增用户数
- 每日订单数量
- 每个分类下的商品数量
- 每个用户的订单数量

**示例：统计每天新增用户数量**

```sql
SELECT
    DATE(create_time) AS date,
    COUNT(*) AS new_users
FROM user
WHERE create_time >= #{beginTime}
  AND create_time < #{endTimeExclusive}
GROUP BY DATE(create_time)
ORDER BY DATE(create_time);
```

注意：`create_time` 需要替换为项目实际使用的用户注册时间字段。

**核心语法：**

- `COUNT(*)`：统计符合条件的记录数量。
- `GROUP BY`：按日期分组。

**记忆：** 需要统计每个分组里面有多少条记录，使用 `COUNT + GROUP BY`。

---

## 3. CASE WHEN：条件判断与条件聚合

**适用场景：**

- 每日总订单数与有效订单数
- 已完成订单与取消订单数量
- 不同状态的订单分别统计
- 按条件统计金额

**示例：同时统计每日订单总数和有效订单数**

```sql
SELECT
    DATE(order_time) AS date,
    COUNT(*) AS total_orders,
    SUM(
        CASE
            WHEN status = 5 THEN 1
            ELSE 0
        END
    ) AS valid_orders
FROM orders
WHERE order_time >= #{beginTime}
  AND order_time < #{endTimeExclusive}
GROUP BY DATE(order_time);
```

这里的 `status = 5` 仅为示例，实际需要按照项目的有效订单定义确定。

**理解 CASE WHEN：**

```sql
CASE
    WHEN 条件 THEN 1
    ELSE 0
END
```

符合条件返回 1，否则返回 0。

再通过 `SUM()` 累加，就可以统计符合条件的记录数量。

也可以统计金额：

```sql
SUM(
    CASE
        WHEN status = 5 THEN amount
        ELSE 0
    END
)
```

**记忆：** 同一批数据需要按照不同条件统计多个指标时，优先考虑 `CASE WHEN + SUM`。

---

## 4. JOIN + GROUP BY：多表关联统计

**适用场景：**

- 商品销量统计
- 用户消费统计
- 订单明细汇总
- 需要同时使用主表和附表字段的统计

**示例：统计商品销量 Top10**

```sql
SELECT
    od.name,
    SUM(od.number) AS sales
FROM order_detail od
INNER JOIN orders o
    ON od.order_id = o.id
WHERE o.order_time >= #{beginTime}
  AND o.order_time < #{endTimeExclusive}
  AND o.status = #{validStatus}
GROUP BY od.name
ORDER BY sales DESC
LIMIT 10;
```

**核心语法：**

- `JOIN`：关联两张表。
- `ON`：指定关联条件。
- `SUM(od.number)`：累计商品购买数量。
- `GROUP BY`：按照商品分组。
- `ORDER BY sales DESC`：按照销量降序排列。
- `LIMIT 10`：只取前十条。

**记忆：** 当需要统计的数据分散在多张表中，使用 JOIN 关联后再进行聚合。

注意：按商品名称分组可能合并不同的同名商品。真实业务中更适合按照商品 ID 和类型分组。

---

## 5. ORDER BY + LIMIT：排序与 TopN

**适用场景：**

- 商品销量排行榜
- 用户消费排行榜
- 热门商品排行
- 最近创建的订单
- 最新记录查询

**示例：取销量最高的前十名**

```sql
ORDER BY sales DESC
LIMIT 10;
```

其他常见写法：

```sql
-- 取前 5 条
LIMIT 5;

-- 从第 10 条开始，取 10 条
LIMIT 10, 10;
```

**记忆：** 排名、TopN、分页，通常需要 `ORDER BY + LIMIT`。

---

## 6. 前缀和：累计统计

**适用场景：**

- 每日累计用户数
- 每日累计订单数
- 每日累计销售额
- 累计库存变化

假设需要查询 11-12 至 11-15 的累计用户数。

累计用户数的计算逻辑：

\[
Total_i=Users_{before}+\sum_{j=begin}^{i}NewUsers_j
\]

推荐采用两次 SQL + Java 前缀和。

**SQL 1：查询每日新增用户**

```sql
SELECT
    DATE(create_time) AS date,
    COUNT(*) AS new_users
FROM user
WHERE create_time >= #{beginTime}
  AND create_time < #{endTimeExclusive}
GROUP BY DATE(create_time);
```

**SQL 2：查询开始日期之前的用户总数**

```sql
SELECT COUNT(*)
FROM user
WHERE create_time < #{beginTime};
```

**Java：计算前缀和**

```java
long total = totalBefore;

for (LocalDate date : dateList) {
    long daily = dailyMap.getOrDefault(date, 0L);
    total += daily;

    // daily：当天新增用户数
    // total：截至当天的累计用户数
}
```

另一种方式是使用 SQL 窗口函数：

```sql
SUM(new_users) OVER (
    ORDER BY stat_date
    ROWS BETWEEN UNBOUNDED PRECEDING
    AND CURRENT ROW
)
```

它可以直接在查询结果中计算累计和。

**记忆：** 累计值 = 初始值 + 每日新增值的前缀和。

---

## 7. 日期范围：左闭右开

这是统计 SQL 中非常重要的一个习惯。

推荐：

```sql
WHERE order_time >= #{beginTime}
  AND order_time < #{endTimeExclusive}
```

假设用户查询：

2026-11-15 至 2026-11-24（包含首尾）。

Java 传入：

```java
beginTime = 2026-11-15T00:00:00;
endTimeExclusive = 2026-11-25T00:00:00;
```

这样就能完整覆盖 11 月 24 日。

**为什么不使用 BETWEEN？**

因为 BETWEEN 两端通常都是包含的，使用左闭右开更容易处理连续日期区间，也能避免时间精度带来的边界问题。

---

## 8. COALESCE：处理 NULL

**适用场景：**

- 没有订单时将营业额设置为 0
- 没有统计结果时补默认值
- 聚合查询结果为空时处理空值

示例：

```sql
SELECT COALESCE(SUM(amount), 0)
FROM orders
WHERE status = 5;
```

含义：

如果 `SUM(amount)` 返回 NULL，就返回 0。

注意：`GROUP BY` 不会自动生成没有数据的日期，需要 Java 补齐日期，或者通过日期表/CTE 生成完整日期序列。

---

## 9. 统计报表的 SQL 组合速查表

| 业务需求 | 推荐 SQL |
|---|---|
| 每日营业额 | `SUM + GROUP BY` |
| 每日新增用户 | `COUNT + GROUP BY` |
| 每日订单总量 | `COUNT + GROUP BY` |
| 每日有效订单量 | `CASE WHEN + SUM` |
| 每日多个条件指标 | `CASE WHEN + GROUP BY` |
| 商品销量 Top10 | `JOIN + SUM + GROUP BY + ORDER BY + LIMIT` |
| 累计用户量 | `COUNT + Java 前缀和` |
| 累计值进阶计算 | `SUM() OVER()` |
| 空值处理 | `COALESCE` |
| 日期范围过滤 | 左闭右开 |

---

## 10. 后端统计开发的几个经验

### ① 能在数据库一次完成的聚合，尽量不要逐条查询

不推荐：

```java
for (LocalDate date : dateList) {
    // 每天查询一次数据库
}
```

推荐：

```text
SQL 一次 GROUP BY
        ↓
Java 获取结果
        ↓
Map 转换
        ↓
补齐没有数据的日期
        ↓
组装 VO
```

### ② 数据库负责数据聚合，Java 负责业务组织

适合数据库：
- SUM
- COUNT
- GROUP BY
- JOIN
- 排序
- 条件聚合

适合 Java：
- 生成完整日期列表
- 补齐缺失日期
- 计算简单前缀和
- 组装 VO

### ③ 一条 SQL 不一定永远优于两条 SQL

不要为了减少 SQL 数量而强行写复杂查询。

例如累计用户统计，采用两次 SQL + Java 前缀和，比复杂的递归 CTE 更容易维护。

### ④ 注意数据库索引

日期范围过滤尽量直接作用于原始时间字段：

```sql
WHERE order_time >= ?
  AND order_time < ?
```

不要轻易写成：

```sql
WHERE DATE(order_time) = ?
```

后者可能使普通时间索引无法有效用于范围检索。

具体执行计划可以使用 `EXPLAIN` 检查。

---

**本篇核心总结：**

遇到统计需求时，先思考四件事：

1. 数据在哪张表？是否需要 JOIN？
2. 按什么维度统计？是否需要 GROUP BY？
3. 统计什么指标？SUM、COUNT 还是 CASE WHEN？
4. 是否需要排序、TopN 或累计计算？

掌握这几类 SQL 组合，就能解决相当多的后台管理统计需求。