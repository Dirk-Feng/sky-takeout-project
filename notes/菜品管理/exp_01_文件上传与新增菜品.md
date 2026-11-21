# TASK07 — 文件上传与新增菜品

## 一、OSS 文件上传与环境配置

### 1. 文件上传流程

前端通过 `multipart/form-data` 提交文件，后端使用 Spring 的 `MultipartFile` 接收。

调用链：

```text
Vue 前端
  ↓ POST /admin/common/upload
Nginx 转发
  ↓
CommonController
  ↓ 文件校验、UUID 命名
AliOssUtil
  ↓ OSS SDK
阿里云 OSS Bucket
  ↓ 返回图片 URL
Result<String>
  ↓
前端回显图片
```

核心设计：

- `MultipartFile`：接收 HTTP 上传文件。
- `ImageIO`：读取文件实际内容，验证图片格式，而不是只相信文件名。
- `UUID.randomUUID()`：生成唯一文件名，避免重名覆盖。
- `AliOssUtil`：封装第三方 OSS SDK 调用。
- OSS 保存图片本体，后续 MySQL 只需要保存图片 URL。

### 2. Spring Profile 环境配置

项目使用 Spring Profile 区分运行环境：

```yaml
spring:
  profiles:
    active: dev
```

可以分别维护：

- `application.yml`：通用配置。
- `application-dev.yml`：开发环境配置。
- `application-prod.yml`：生产环境配置。

通过切换 Profile，让不同环境使用不同的数据库、OSS 等服务配置。

### 3. 敏感信息管理

本项目 OSS 配置使用环境变量，而不是将 AccessKey 写死在配置文件中。

`application-dev.yml` 中通过占位符读取：

```yaml
sky:
  alioss:
    endpoint: ${OSS_ENDPOINT}
    bucket-name: ${OSS_BUCKET_NAME}
    access-key-id: ${OSS_ACCESS_KEY_ID}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET}
```

本地启动时，在 IDE 的 Run Configuration 中配置对应环境变量：

| 环境变量 | 含义 |
|---|---|
| `OSS_ENDPOINT` | Bucket 所在地域的访问节点 |
| `OSS_BUCKET_NAME` | Bucket 名称 |
| `OSS_ACCESS_KEY_ID` | RAM 用户 AccessKey ID |
| `OSS_ACCESS_KEY_SECRET` | RAM 用户 AccessKey Secret |

本项目使用杭州地域：

`oss-cn-hangzhou.aliyuncs.com`

**配置管理原则：**

1. Profile 负责区分开发、测试、生产环境。
2. 环境变量负责注入各环境的具体配置。
3. AccessKey Secret 等敏感信息不写入源码，不提交 Git。
4. 配置文件可以提交，但必须确保其中没有真实密钥。
5. 生产环境通过部署环境注入生产凭证，而不是复用开发环境凭证。

这样既实现了环境隔离，也避免了凭证泄露。

---

## 二、新增菜品：Dish + DishFlavor

接口：

`POST /admin/dish`

### 1. 数据关系

一个 Dish 可以对应多个 DishFlavor：

```text
Dish
 ├── DishFlavor (dish_id)
 ├── DishFlavor (dish_id)
 └── DishFlavor (dish_id)
```

`dish_flavor.dish_id` 是业务上的逻辑外键，用于关联所属菜品。

### 2. 核心业务流程

```text
Controller 接收 DishDTO
        ↓
Service 开启事务
        ↓
DishMapper.insert(dish)
        ↓
MyBatis 回填自增主键
        ↓
获取 dish.getId()
        ↓
为每条 DishFlavor 设置 dishId
        ↓
DishFlavorMapper.insertBatch()
        ↓
事务提交
```

### 3. 主键回填

使用 MyBatis：

```java
@Options(
    useGeneratedKeys = true,
    keyProperty = "id"
)
```

执行 INSERT 后，MyBatis 将数据库生成的自增主键回填到传入的 Dish 对象。

因此不需要额外查询刚刚插入的菜品 ID。

随后由后端统一设置每条风味记录的 `dishId`，不信任前端传入的关联 ID。

### 4. 事务管理

在 Service 的完整业务方法上使用：

```java
@Transactional
```

将 Dish 和 DishFlavor 的写入纳入同一个事务。

- 全部成功：提交。
- 任意一步失败：回滚。

本项目中 DishFlavor 没有审计字段，因此不需要使用之前的 AutoFill AOP；Dish 本身继续复用 AutoFill。

---

## 三、本次功能验收

| 项目 | 结果 |
|---|---|
| JDK 17 Maven 编译 | PASS |
| OSS 文件上传 | PASS |
| 前端图片回显 | PASS |
| 新增菜品前后端联调 | PASS |
| MySQL Dish 插入 | PASS |
| MySQL DishFlavor 关联 | PASS |
| 主键回填 | PASS |
| 人为制造风味插入异常，验证事务回滚 | PASS |

## 四、本次学习收获

1. 第三方云存储与业务数据库分离：OSS 保存文件，MySQL 保存 URL。
2. 使用 Spring Profile 和环境变量管理多环境配置及敏感信息。
3. 使用 MyBatis 主键回填完成父子表关联。
4. 使用 Service 事务保证多表写入的原子性。
5. 复用已有 AOP 公共字段填充能力，减少重复代码。

本次重点不在于记住 OSS SDK 或 ImageIO 的具体 API，而是理解第三方服务集成、配置隔离和多表业务写入的工程设计思路。