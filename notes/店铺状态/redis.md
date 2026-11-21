# Redis 实战：店铺营业状态管理

## 一、业务需求

店铺营业状态只有两个值：

- `1`：营业中
- `0`：打烊

管理员可以修改营业状态，用户端可以获取当前营业状态。

由于该数据具有以下特点：

1. 全局只有一个状态值，不涉及复杂的数据关系。
2. 管理员负责修改，用户端需要频繁读取。
3. 不需要复杂的查询和事务操作。

因此没有必要专门为营业状态创建 MySQL 数据表，直接使用 Redis 保存即可。

Redis Key：

```text
shop:status
```

Redis Value：

```text
1  // 营业中
0  // 打烊
```

基本操作：

```redis
SET shop:status 1
GET shop:status
```

## 二、整体架构设计

本次采用标准三层架构，没有因为业务简单而直接在 Controller 中操作 Redis。

```text
AdminShopController       UserShopController
        |                         |
        |                         |
        +----------+--------------+
                   |
                   v
               ShopService
                   |
                   v
             ShopServiceImpl
                   |
                   v
              RedisTemplate
                   |
                   v
              Redis Server
```

### 1. Controller 层

分别创建两个 Controller：

- `AdminShopController`：管理员修改营业状态。
- `UserShopController`：用户查询营业状态。

两个 Controller 使用不同的类名，避免 Spring Bean 命名冲突，也方便后续项目规模扩大后定位代码。

### 2. Service 层

统一封装营业状态的读写操作：

```java
void setStatus(Integer status);

Integer getStatus();
```

- `setStatus()`：将营业状态写入 Redis。
- `getStatus()`：从 Redis 获取营业状态。

两个 Controller 共用同一个 ShopService，而不是各自直接操作 Redis。

### 3. Redis 层

使用 RedisTemplate（或适合字符串操作的 StringRedisTemplate）完成 Redis 数据读写。

Redis 中只保存一份 `shop:status`，管理员修改后，用户端读取的就是最新状态。

## 三、为什么使用 Redis 而不是 MySQL？

这体现了不同存储组件的职责划分：

| 存储组件 | 适合的数据 |
|---|---|
| MySQL | 用户、员工、菜品、订单等持久化业务数据 |
| Redis | 缓存、临时数据、共享运行状态等 |

Redis 不只是缓存，也可以承担轻量级的业务状态存储职责。

本次营业状态就是一个典型例子：数据简单、访问频繁、多个业务入口需要共享。

## 四、RedisTemplate 与 Spring IoC

之前通过 RedisConfiguration 配置 RedisTemplate：

```java
@Bean
public RedisTemplate redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate redisTemplate = new RedisTemplate();

    redisTemplate.setConnectionFactory(factory);
    redisTemplate.setKeySerializer(new StringRedisSerializer());

    return redisTemplate;
}
```

几个核心组件：

| 组件 | 职责 |
|---|---|
| RedisTemplate | 封装 Redis 命令操作 |
| RedisConnectionFactory | 管理 Redis 连接 |
| StringRedisSerializer | 将字符串序列化为字节数据 |
| RedisConfiguration | 配置并注册 RedisTemplate Bean |

Spring Boot 启动时创建并注册 RedisTemplate Bean，业务层通过依赖注入直接使用。

需要注意：当前示例只设置了 Key 的序列化器，Value 的序列化策略也需要根据实际数据类型配置。对于营业状态这种简单的 0/1 数据，应保证 Redis 中能够直观读取。

## 五、Java 如何与 Redis 通信？

本地开发环境：

- Windows 11：运行 Spring Boot。
- Docker Desktop：提供 Linux 容器运行环境。
- Redis Container：运行 Redis Server。
- Redis 端口：6379。

通信流程：

```text
Spring Boot
     |
     v
RedisTemplate
     |
     v
RedisConnectionFactory
     |
     v
TCP 网络连接
     |
     v
127.0.0.1:6379
     |
     v
Docker 端口映射
     |
     v
Redis Server
```

RedisTemplate 底层通过 Redis 客户端驱动管理连接，发送 Redis 协议命令并接收执行结果。

业务代码不需要直接处理 TCP 连接和 Redis 协议。

## 六、值得注意的设计问题

1. **统一 Redis Key**：使用常量定义 `shop:status`，避免多个业务类重复硬编码。
2. **状态校验**：管理员只能设置 0 或 1，非法值不应写入 Redis。
3. **初始状态**：如果 Redis 中不存在 `shop:status`，读取结果可能为 null，需要根据业务约定处理初始化状态。
4. **角色隔离**：管理员负责修改，用户负责读取，但操作的是同一个 Redis Key。
5. **业务分层**：即使只是一次简单的 Redis SET/GET，也建议通过 Service 统一封装，保持项目结构一致。

## 七、本节总结

本次功能的核心收获：

- 掌握 Redis 在实际业务中保存全局运行状态的用法。
- 理解 RedisTemplate 在 Spring Boot 中的作用。
- 理解 RedisConnectionFactory 和序列化器的职责。
- 理解不同 Controller 可以通过同一个 Service 访问共享数据。
- 进一步巩固 Spring IoC、DI 和三层架构。

**设计思想：业务代码关注业务逻辑，Service 负责业务封装，RedisTemplate 负责底层 Redis 操作。不要因为业务简单就随意破坏项目的分层结构。**

## 八、后续 TODO

- [ ] 套餐管理完成后，考虑套餐停售状态与店铺营业状态之间的关系。
- [ ] 考虑打烊状态下是否需要限制用户下单。
- [ ] 进一步学习 Redis 与 Spring Boot 的缓存集成。