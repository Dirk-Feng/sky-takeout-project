# TASK07 — 用户提交订单（Submit Order）

## 一、模块概述

接口：`POST /user/order/submit`

涉及的主要数据库表：

| 表名 | 作用 |
|---|---|
| address_book | 获取收货地址 |
| shopping_cart | 获取待下单商品 |
| dish | 校验菜品状态和价格 |
| setmeal | 校验套餐状态和价格 |
| orders | 保存订单主信息 |
| order_detail | 保存订单商品明细 |

本模块是之前 CRUD、事务、MyBatis、业务校验和接口安全等知识的综合应用。

## 二、核心业务流程

1. 从 BaseContext 获取当前登录用户 ID。
2. 校验 DTO 请求参数。
3. 查询收货地址并验证用户归属。
4. 查询并锁定当前用户购物车，判断是否为空。
5. 收集菜品和套餐 ID，批量查询商品信息。
6. 校验商品状态、价格和数量，构造订单明细对象。
7. 服务端计算订单金额，并与前端提交的金额比较。
8. 构造订单对象，保存收货信息快照。
9. 插入订单主表，获取数据库生成的订单 ID。
10. 将订单 ID 赋值给所有订单明细，批量插入明细表。
11. 删除本次下单对应的购物车记录。
12. 返回 OrderSubmitVO。

整个方法使用：

```java
@Transactional(rollbackFor = Exception.class)
```

保证订单、订单明细和购物车修改的事务原子性。

## 三、数据库冗余字段与历史快照

数据库设计不一定要求所有字段只存在于一张表中。合理的字段冗余具有实际业务价值。

例如，order_detail 中除了保存 dishId，还保存：

- 商品名称
- 商品图片
- 商品成交单价
- 商品口味
- 商品数量

orders 中除了保存 addressBookId，还保存：

- 收货人
- 联系电话
- 完整收货地址

冗余字段的两个主要作用：

1. 减少关联查询，提高查询效率。
2. 保存历史业务快照，避免原始数据修改后影响历史订单。

例如，用户以 25 元购买商品，之后商家将商品价格修改为 30 元，历史订单仍应显示成交时的 25 元。

**数据库设计需要在规范化、查询效率、数据一致性和实际业务需求之间进行权衡。冗余不一定是坏事。**

## 四、水平越权与数据可信边界

JWT 身份认证只能确认当前用户是谁，不能证明该用户有权操作任意数据库记录。

下单时，前端传入的 addressBookId 可能被人为修改，因此必须校验地址归属：

```sql
SELECT *
FROM address_book
WHERE id = #{addressBookId}
  AND user_id = #{userId};
```

其中：

- addressBookId 来自前端。
- userId 来自后端 JWT 认证上下文。

清理购物车时同样需要限定当前登录用户。

核心原则：

**身份认证（Authentication）解决用户是谁，权限校验（Authorization）解决用户可以操作哪些资源。**

## 五、服务端金额计算

课程原始实现直接接收前端计算的订单金额，存在金额被人为篡改的风险。

当前项目采用服务端重新计算并校验的方式。

费用规则：

- 配送费：6 元/订单。
- 打包费：1 元/份商品。
- 商品价格：以服务端数据库为准。
- 商品数量：以当前用户购物车为准。

订单总金额：

```text
商品总金额 = Σ（商品单价 × 商品数量）

打包费 = 商品总份数 × 单位打包费

订单总金额 = 商品总金额 + 配送费 + 打包费
```

配送费和单位打包费通过配置文件管理：

```yaml
order-fees:
  delivery: 6.00
  packaging-per-item: 1.00
```

通过构造器和 `@Value` 注入 Service。

如果商品已经下架、价格发生变化，或者前端提交金额与服务端计算金额不一致，则拒绝下单。

前端可以计算金额用于展示，但最终交易金额必须由后端确认。

当前 packAmount 使用整数类型，因此费用配置仍受到数据模型和前端接口语义的限制，不能认为打包费已经支持任意小数金额。

## 六、BigDecimal 金额计算

浮点数存在精度误差，例如：

```java
System.out.println(0.1 + 0.2);
// 0.30000000000000004
```

因此涉及金额的业务通常使用 BigDecimal，数据库对应使用 DECIMAL 类型。

常用方法：

```java
a.add(b);       // 加法
a.subtract(b);  // 减法
a.multiply(b);  // 乘法
a.compareTo(b); // 数值比较
```

BigDecimal 是不可变对象，计算后需要重新赋值：

```java
subtotal = subtotal.add(
    price.multiply(BigDecimal.valueOf(number))
);
```

金额比较优先使用：

```java
a.compareTo(b) == 0
```

而不是 equals，因为 6.0 和 6.00 的数值相同，但 scale 不同。

避免直接使用 `new BigDecimal(0.1)`，优先使用字符串构造或 `BigDecimal.valueOf()`。

## 七、批量查询与批量插入

### 1. 避免 N+1 查询

先遍历购物车，使用 Set 收集并去重商品 ID。

然后通过 SQL IN 批量查询菜品和套餐，将查询结果转换为 Map：

```java
Map<Long, Dish> dishMap;
Map<Long, Setmeal> setmealMap;
```

后续遍历购物车时，直接根据 ID 从 Map 获取商品信息，不需要重复查询数据库。

### 2. MyBatis 主键回填

插入 orders 表时，通过 MyBatis 的主键回填机制，将 MySQL 生成的自增主键写回 Java 对象。

典型配置：

```xml
useGeneratedKeys="true"
keyProperty="id"
```

插入成功后，可以通过：

```java
order.getId();
```

获取订单 ID，并赋值给所有订单明细。

### 3. 批量插入订单明细

Service 层可以使用循环构造 List，但不要在循环中逐条调用 Mapper。

使用 MyBatis foreach 生成多 VALUES INSERT：

```sql
INSERT INTO order_detail (...)
VALUES
    (...),
    (...),
    (...);
```

减少数据库往返次数。

## 八、MySQL 悲观锁（本模块新增重点）

### 1. SELECT FOR UPDATE

当前项目查询购物车时使用：

```java
@Select("select * from shopping_cart " +
        "where user_id = #{userId} " +
        "order by id for update")
List<ShoppingCart> listForOrder(Long userId);
```

普通 SELECT 通常属于快照读，不会阻止其他事务修改记录。

SELECT FOR UPDATE 属于锁定读，在 InnoDB 中会读取数据并对相关记录加排他锁，属于悲观锁的一种实现。

### 2. 为什么下单时需要加锁？

假设用户连续点击两次提交订单，产生两个并发请求 A 和 B。

不加锁：

```text
A 读取购物车
B 读取相同购物车
A 创建订单
B 尝试创建订单
```

两个请求可能同时处理同一批商品。

加锁：

```text
A：SELECT FOR UPDATE，获取锁
B：SELECT FOR UPDATE，等待锁释放

A：创建订单
A：插入订单明细
A：删除购物车
A：事务提交，释放锁

B：恢复查询
B：发现购物车为空
B：拒绝下单
```

在上述场景中，可以避免两个请求重复处理同一批购物车记录。

同时，下单过程中其他事务对已锁定购物车记录的修改或删除也需要等待。

### 3. 锁与事务的配合

FOR UPDATE 获取的锁不会在 SELECT 执行结束后立即释放，而是持有到当前事务提交或回滚。

因此必须配合外层事务使用。

| 对比 | 事务 | 锁 |
|---|---|---|
| 主要作用 | 保证业务操作的整体性 | 处理并发访问冲突 |
| 本项目实现 | @Transactional | FOR UPDATE |
| 下单场景 | 避免产生不完整订单 | 避免重复处理购物车 |

事务和锁解决的问题不同，但经常配合使用。

### 4. 悲观锁与乐观锁

| 对比 | 悲观锁 | 乐观锁 |
|---|---|---|
| 思想 | 提前防止并发冲突 | 更新时检查是否发生冲突 |
| 常见实现 | SELECT FOR UPDATE | version 版本号 |
| 冲突处理 | 等待、超时或死锁处理 | 更新失败、重试或拒绝 |

当前项目使用悲观锁。

### 5. 索引与锁范围

FOR UPDATE 的实际加锁范围取决于：

- 数据库索引
- SQL 查询条件
- 执行计划
- 事务隔离级别

如果 shopping_cart.user_id 缺少合适的索引，可能导致数据库扫描并锁定远超预期范围的数据。

可以通过以下 SQL 检查索引：

```sql
SHOW INDEX FROM shopping_cart;
```

索引不仅影响查询效率，也会影响加锁范围和并发性能。

ORDER BY id 可以使查询结果顺序稳定，但不能单独保证数据库实际加锁顺序。

后续准备 MySQL 面试时再系统学习：

- Record Lock（记录锁）
- Gap Lock（间隙锁）
- Next-Key Lock（临键锁）
- MVCC
- 事务隔离级别
- 死锁检测

## 九、接口幂等性（初步了解）

如果服务器已经创建订单，但客户端因为网络问题没有收到响应，客户端可能再次发送请求。

幂等性要求同一次业务请求重复执行时，不应产生额外的业务副作用。

当前项目通过购物车加锁、事务和删除行数校验，可以防止部分重复下单问题，但尚未实现完整的接口幂等性。

常见解决方案：

- 唯一请求 ID
- 数据库唯一约束
- Redis 防重复提交 Token

后续结合支付模块进一步学习。

## 十、代码可维护性

### 1. MyBatis 注解与 XML

MyBatis 支持在注解中使用 script 和 foreach 编写动态 SQL。

IDEA 可能出现：

```text
<statement> or DELIMITER expected, got '<'
```

原因是 SQL 静态检查器可能无法识别 MyBatis 动态 SQL 标签，并不一定代表实际运行错误。

推荐开发习惯：

- 简单静态 SQL：使用 Mapper 注解。
- 复杂动态 SQL：使用 Mapper XML。

### 2. Builder 构建对象

使用 Lombok Builder 代替大量 Setter：

```java
Orders order = Orders.builder()
        .userId(userId)
        .amount(total)
        .status(Orders.PENDING_PAYMENT)
        .build();
```

Builder 可以提高对象构建代码的可读性，但不能自动保证字段完整性和业务正确性。

## 十一、当前模块总结

本模块重点掌握：

- 数据库冗余字段与历史快照。
- 水平越权防护。
- 服务端金额计算与数据可信边界。
- BigDecimal 精确金额计算。
- 批量查询、批量插入与主键回填。
- 多步数据库操作的事务原子性。
- SELECT FOR UPDATE 悲观锁。
- 索引与数据库加锁范围的关系。
- 接口幂等性的基本概念。

当前代码已完成实现和阅读，能够正常运行。完整的真实数据库事务回滚、并发下单及索引检查结果尚未在学习记录中确认。

---

**核心工程经验：**

1. 不要无条件信任客户端传入的数据。
2. 合理的数据库冗余能够保留历史业务状态。
3. 尽量减少不必要的数据库往返操作。
4. 事务保证业务操作的整体性，锁处理并发冲突。
5. 业务功能能够正常运行，不代表已经解决所有安全性和并发问题。