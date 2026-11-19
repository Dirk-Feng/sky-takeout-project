# TASK07 — AOP 公共字段自动填充（AutoFill）

## 一、功能背景与设计目的

在苍穹外卖项目中，员工管理、分类管理等业务都需要维护数据库中的公共审计字段：

| 字段 | 含义 | INSERT | UPDATE |
|---|---|---|---|
| create_time | 创建时间 | 填充 | 不修改 |
| create_user | 创建人 ID | 填充 | 不修改 |
| update_time | 最后修改时间 | 填充 | 填充 |
| update_user | 最后修改人 ID | 填充 | 填充 |

在没有自动填充机制之前，这些字段需要在各个 Service 中重复赋值。

例如，新增员工和新增分类都需要设置相同的四个字段，修改员工和修改分类也需要重复设置更新时间和操作人。

这些逻辑不属于具体业务，而是多个业务共同需要的基础功能。

因此，项目使用 **Spring AOP + 自定义注解 + Java 反射**，实现统一的公共字段自动填充。

核心思想：

**将业务代码中的公共字段赋值逻辑抽离，在 Mapper 方法执行之前，由切面统一完成填充。**

## 二、整体架构

本次实现涉及三个主要组件：

### 1. OperationType

位置：`sky-common`

这是项目原本已经存在的枚举，包含：

- INSERT
- UPDATE

用于区分当前 Mapper 操作属于新增还是修改。

### 2. AutoFill 自定义注解

位置：`sky-server/annotation`

用于标记哪些 Mapper 方法需要自动填充。

示例：

```java
@AutoFill(OperationType.INSERT)
int insert(Employee employee);

@AutoFill(OperationType.UPDATE)
int update(Employee employee);
```

注解的主要作用是提供操作类型，让切面知道当前应该填充哪些字段。

相关元注解：

- `@Target(ElementType.METHOD)`：注解只能作用于方法。
- `@Retention(RetentionPolicy.RUNTIME)`：运行期间保留注解，供 AOP 和反射读取。
- `value()`：接收 OperationType，区分 INSERT 和 UPDATE。

注解本身不执行填充操作，只负责标记。

### 3. AutoFillAspect 切面类

位置：`sky-server/aspect`

负责真正执行自动填充逻辑。

主要使用：

- `@Aspect`：声明当前类为切面。
- `@Component`：交给 Spring 管理。
- `@Pointcut`：筛选需要拦截的 Mapper 方法。
- `@Before`：在目标方法执行之前进行字段填充。
- `JoinPoint`：获取当前方法调用的信息。
- Java Reflection：动态调用实体对象的 Setter。

## 三、AOP 执行流程

整体调用过程：

```text
Controller
    ↓
Service
    ↓
EmployeeMapper.insert(employee)
    ↓
Spring AOP 判断是否匹配切入点
    ↓
检查是否存在 @AutoFill
    ↓
AutoFillAspect 执行 @Before
    ↓
获取当前 Mapper 方法及参数
    ↓
获取 AutoFill 注解中的 OperationType
    ↓
获取第一个参数（实体对象）
    ↓
准备公共字段数据
    ↓
根据 INSERT / UPDATE 判断填充内容
    ↓
使用反射调用实体 Setter
    ↓
原 Mapper 方法继续执行
    ↓
MyBatis 读取实体属性并生成 SQL
    ↓
MySQL 执行数据库操作
```

这里需要特别理解：

**AOP 修改的是传入 Mapper 的 Java 实体对象，而不是直接修改 SQL。**

切面完成字段赋值后，MyBatis 读取已经更新的实体属性，正常执行原有 SQL。

## 四、JoinPoint 的作用

JoinPoint 表示当前被拦截的方法调用，可以获取方法信息和传入参数。

主要使用两个能力：

### 1. 获取方法签名

通过 `joinPoint.getSignature()` 获取当前执行的方法信息。

再通过 MethodSignature 获取具体的 Method。

### 2. 获取方法参数

```java
Object[] args = joinPoint.getArgs();
```

获取当前方法的所有参数。

本项目提前约定：

**所有需要自动填充的 Mapper 方法，第一个参数必须是实体对象。**

因此：

```java
Object entity = args[0];
```

就能获取当前需要填充的实体。

例如：

- EmployeeMapper.insert(Employee)
- CategoryMapper.insert(Category)

虽然两个方法传入的对象类型不同，但都可以通过 Object 接收。

这也是自动填充能够跨业务复用的基础。

## 五、Java 反射：如何动态填充字段？

这是本次学习中比较重要的技术点。

### 1. 为什么需要反射？

Employee 和 Category 是不同的 Java 类。

如果直接使用普通代码，需要分别调用：

```java
employee.setUpdateTime(now);
category.setUpdateTime(now);
```

但切面需要编写一套通用逻辑，不能提前写死实体类型。

因此使用反射，在运行期间获取实体的实际类型，并动态查找对应的 Setter。

### 2. 获取实体实际类型

```java
Class<?> clazz = entity.getClass();
```

例如：

- entity 实际是 Employee，就得到 Employee.class。
- entity 实际是 Category，就得到 Category.class。

### 3. 获取 Setter 方法

本项目实际采用 `getMethod()`：

```java
Method updateTime = entity.getClass()
    .getMethod("setUpdateTime", LocalDateTime.class);

Method updateUser = entity.getClass()
    .getMethod("setUpdateUser", Long.class);
```

`getMethod()` 属于 Java Class 类的方法。

作用是根据方法名称和参数类型，查找当前类或父类中的 public 方法。

这里：

- `"setUpdateTime"`：要寻找的方法名称。
- `LocalDateTime.class`：Setter 的参数类型。
- `"setUpdateUser"`：操作人 ID 对应的 Setter。
- `Long.class`：操作人 ID 的参数类型。

查找成功后返回 Method 对象。

### 4. invoke 动态调用

```java
updateTime.invoke(entity, now);
updateUser.invoke(entity, currentId);
```

`invoke()` 才是真正执行方法的操作。

可以理解为：

```java
entity.setUpdateTime(now);
entity.setUpdateUser(currentId);
```

只不过这里不需要提前知道 entity 的具体类型，而是通过反射在运行期间完成调用。

### 5. getMethod 与 getDeclaredMethod 的区别

| 方法 | 查找范围 |
|---|---|
| getMethod() | 当前类及父类中的 public 方法 |
| getDeclaredMethod() | 当前类自己声明的方法，包括 private、protected、public |

本项目采用 getMethod()，因为我们需要调用的是实体对外公开的 Setter。

这样切面只依赖实体的公共接口，不需要直接访问 private 字段。

需要注意：反射查找方法时必须匹配准确的参数类型。例如 `Long.class` 和 `long.class` 并不相同。

## 六、INSERT 和 UPDATE 的差异

切面通过读取 AutoFill 注解中的 OperationType，决定需要填充哪些字段。

### INSERT

新增数据时：

```text
createTime = 当前时间
createUser = 当前登录用户 ID
updateTime = 当前时间
updateUser = 当前登录用户 ID
```

### UPDATE

修改数据时：

```text
updateTime = 当前时间
updateUser = 当前登录用户 ID
```

不会覆盖原有创建时间和创建人。

这样可以保证审计字段的业务含义正确。

## 七、ThreadLocal 与当前操作人

自动填充还需要知道当前是谁在执行数据库操作。

这里复用了此前实现的 BaseContext。

完整流程：

```text
管理员登录
    ↓
生成 JWT（包含 EMP_ID）
    ↓
前端携带 JWT 发起请求
    ↓
JwtTokenAdminInterceptor
    ↓
解析 JWT，获取 EMP_ID
    ↓
BaseContext.setCurrentId(empId)
    ↓
Controller → Service → Mapper
    ↓
AutoFillAspect
    ↓
BaseContext.getCurrentId()
    ↓
填充 createUser / updateUser
```

ThreadLocal 的作用是让当前请求线程中的不同业务层都能获取当前用户 ID，而不需要每一层都显式传递这个参数。

请求完成后，拦截器负责清理 ThreadLocal，避免线程复用造成上下文污染。

因此，AutoFill 依赖 JWT 拦截器提前建立用户上下文。

## 八、本次工程中的实际接入

本次实现：

1. 复用了 sky-common 中已有的 OperationType。
2. 在 sky-server 中新增 AutoFill 注解。
3. 在 sky-server 中新增 AutoFillAspect 切面。
4. EmployeeMapper 的实体 insert、update 方法增加 AutoFill 注解。
5. CategoryMapper 的实体 insert、update 方法增加 AutoFill 注解。
6. 删除对应 Service 中重复的公共字段赋值代码。
7. 密码专用更新方法没有接入 AutoFill，因为它的首参数不是实体对象，继续使用原有审计字段处理逻辑。

目前自动填充只作用于显式标记了 `@AutoFill` 的 Mapper 方法，并非所有 SQL 操作都会被自动拦截。

## 九、为什么暂时放在 sky-server？

虽然 AutoFill 是可复用功能，但不意味着一定要放进 sky-common。

当前实现依赖：

- 苍穹外卖的 Mapper 结构。
- 当前项目的实体字段约定。
- BaseContext 用户上下文。
- Spring AOP。

这些都是当前服务内部的基础设施。

因此目前将注解和切面放在 sky-server 更符合模块职责。

如果未来多个独立后端服务都需要这套能力，可以考虑将它抽取成独立基础设施模块或 Spring Boot Starter。

**代码具有复用价值，不代表必须立即抽成独立 Maven 模块。**

## 十、测试与验收

本次验证结果：

| 验证项目 | 结果 |
|---|---|
| JDK 17 离线 Maven 编译 | PASS |
| 新增员工自动填充 | PASS |
| 修改员工自动填充 | PASS |
| 新增分类自动填充 | PASS |
| 修改分类自动填充 | PASS |
| UPDATE 不覆盖创建字段 | PASS |
| 未标记注解的查询方法不受影响 | PASS |
| 员工状态和密码相关测试 | PASS |
| 员工编辑前后端联调及最后操作时间显示 | PASS |
| 完整应用启动及 MySQL 落库验证 | 未单独验证 |

## 十一、本次学习的核心收获

这次学习并不只是掌握了 AOP 的几个注解，而是理解了如何将重复的基础设施逻辑从业务代码中抽离。

重点掌握：

1. 自定义注解：通过标记方法声明需要执行的公共逻辑。
2. AOP：在 Mapper 方法执行前统一介入。
3. JoinPoint：获取当前方法和参数。
4. 反射：通过 getMethod() 获取 Setter，再通过 invoke() 动态执行。
5. ThreadLocal：获取当前请求对应的登录用户 ID。
6. MyBatis：在实体字段完成填充后，继续执行原有 SQL。

### 最终总结

**AOP 的价值不只是减少重复代码，而是将业务关注点与公共基础设施逻辑分离。**

在苍穹外卖中，员工新增、分类新增等业务只需要声明自己属于 INSERT 或 UPDATE，公共字段填充由切面统一负责。

这让业务 Service 更加专注于业务本身，也降低了以后增加新业务模块时的重复开发成本。