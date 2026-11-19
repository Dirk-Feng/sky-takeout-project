# EMP-02：员工分页查询与 JSON 消息转换

## 一、功能目标

实现员工管理页面的分页查询功能，支持：

- 根据员工姓名模糊搜索。
- 按创建时间倒序排列。
- 支持分页查询。
- 返回前端需要的员工信息。
- 统一日期时间的 JSON 格式。

接口：

`GET /admin/employee/page`

请求参数：

| 参数 | 说明 |
|---|---|
| page | 当前页码 |
| pageSize | 每页记录数 |
| name | 员工姓名，可选 |

响应结构：

`Result<PageResult<EmployeeVO>>`

---

## 二、分页查询：PageHelper + PageInfo

### 1. 整体数据流

```text
Controller
    ↓
EmployeePageQueryDTO
    ↓
Service
    ↓
PageHelper.startPage(page, pageSize)
    ↓
EmployeeMapper
    ↓
MyBatis 执行 SQL
    ↓
PageHelper 插件拦截查询
    ↓
Page<Employee>
    ↓
PageInfo<Employee>
    ↓
EmployeeVO 转换
    ↓
PageResult<EmployeeVO>
    ↓
Result
```

### 2. PageHelper 工作机制

PageHelper 通过 MyBatis 插件机制拦截数据库查询。

执行 `PageHelper.startPage(page, pageSize)` 后，分页参数会保存在当前线程的分页上下文中。

随后 Mapper 执行查询时，PageHelper 拦截 SQL，根据分页参数生成分页查询，并获取总记录数。

查询结果实际是 Page 对象。

### 3. Page 和 PageInfo 的区别

**Page：**

- PageHelper 实际产生的分页结果对象。
- 继承 ArrayList。
- 既包含当前页数据，也包含分页元数据。
- 可以通过 getTotal() 获取总记录数。

**PageInfo：**

- PageHelper 提供的分页信息封装类。
- 接收分页查询返回的 List。
- 能识别其实际对象是否为 Page。
- 提供 getTotal()、getPageNum()、getPageSize()、getPages()、getList() 等方法。

当前项目采用：

```java
PageHelper.startPage(query.getPage(), query.getPageSize());

List<Employee> employees = employeeMapper.pageQuery(query);

PageInfo<Employee> pageInfo = new PageInfo<>(employees);
```

虽然 Mapper 声明返回 List，但 PageHelper 在运行时返回的实际对象是 Page。

因为 Page 继承了 ArrayList，所以可以正常赋值给 List。

使用 PageInfo 可以统一提取分页信息，不需要在 Service 中直接操作 Page 对象。

注意：PageInfo 本身仍属于 PageHelper，并不意味着完全摆脱了 PageHelper 的依赖。

### 4. 为什么使用 PageResult？

PageInfo 是分页组件提供的对象，不适合直接作为项目对外的响应契约。

因此项目使用自己的 PageResult：

```java
PageResult<EmployeeVO> result =
    new PageResult<>(pageInfo.getTotal(), records);
```

职责划分：

- Page：承载实际分页查询结果。
- PageInfo：提取分页元数据。
- PageResult：定义项目对外的分页响应结构。
- Result：统一封装 HTTP 接口响应。

---

## 三、DTO、Entity、VO 的职责

本次分页查询使用了不同的数据对象。

| 对象 | 职责 |
|---|---|
| EmployeePageQueryDTO | 接收前端分页查询条件 |
| Employee | 数据库员工实体 |
| EmployeeVO | 返回给前端的员工信息 |
| PageResult | 分页响应结构 |
| Result | 统一接口响应 |

Employee 转换成 EmployeeVO：

```java
BeanUtils.copyProperties(employee, vo);
```

这里使用的是 Spring BeanUtils，负责 Java 对象之间的属性复制。

由于 EmployeeVO 没有 password 字段，因此不会将密码摘要复制到前端响应。

**设计启发：数据库实体不应该直接等同于前端响应对象。**

Entity 服务于数据库持久化，VO 服务于前端展示。两者应该根据各自的职责独立设计。

---

## 四、Spring MVC 消息转换机制

### 1. 为什么需要 HttpMessageConverter？

Controller 处理的是 Java 对象，而 HTTP 请求和响应传输的是字节数据。

Spring MVC 通过 HttpMessageConverter 实现 HTTP 消息与 Java 对象之间的转换。

常见组件：

- HttpMessageConverter：消息转换器的接口。
- MappingJackson2HttpMessageConverter：使用 Jackson 处理 JSON。
- Jackson ObjectMapper：负责具体的 JSON 序列化和反序列化。

整体关系：

```text
Spring MVC
    ↓
HttpMessageConverter 列表
    ↓
MappingJackson2HttpMessageConverter
    ↓
ObjectMapper
    ↓
JSON 与 Java 对象互相转换
```

### 2. 请求与响应的数据流

请求方向：

```text
前端 JSON
    ↓
MappingJackson2HttpMessageConverter
    ↓
JacksonObjectMapper
    ↓
Java DTO
```

响应方向：

```text
Controller 返回 Java VO
    ↓
MappingJackson2HttpMessageConverter
    ↓
JacksonObjectMapper
    ↓
JSON
    ↓
HTTP Response
    ↓
前端
```

注意：GET 请求的 page、pageSize 等 URL 查询参数由 Spring MVC 参数绑定机制处理，不经过 JSON 请求体反序列化。

---

## 五、自定义 JacksonObjectMapper

### 1. 为什么需要自定义？

Jackson 原本就具备 Java 对象与 JSON 之间的转换能力。

但是项目希望统一日期时间格式，因此通过继承 ObjectMapper，创建自己的 JacksonObjectMapper。

```java
public class JacksonObjectMapper extends ObjectMapper {
    // 自定义序列化和反序列化规则
}
```

当前项目定义的日期格式：

| Java 类型 | JSON 格式 |
|---|---|
| LocalDateTime | yyyy-MM-dd HH:mm |
| LocalDate | yyyy-MM-dd |
| LocalTime | HH:mm:ss |

### 2. Serializer 和 Deserializer

- Serializer：Java 对象转换成 JSON。
- Deserializer：JSON 转换成 Java 对象。

通过 SimpleModule 注册自定义规则：

```java
SimpleModule simpleModule = new SimpleModule()
    .addDeserializer(LocalDateTime.class, ...)
    .addDeserializer(LocalDate.class, ...)
    .addDeserializer(LocalTime.class, ...)
    .addSerializer(LocalDateTime.class, ...)
    .addSerializer(LocalDate.class, ...)
    .addSerializer(LocalTime.class, ...);

this.registerModule(simpleModule);
```

这样只要 Jackson 遇到相应的 Java 类型，就会使用注册的转换规则。

不需要给每个日期字段单独添加 @JsonFormat。

### 3. 为什么可以替换原来的 ObjectMapper？

因为：

```java
public class JacksonObjectMapper extends ObjectMapper
```

JacksonObjectMapper 是 ObjectMapper 的子类。

Java 支持父类引用指向子类对象，因此可以：

```java
converter.setObjectMapper(new JacksonObjectMapper());
```

这里并没有替换整个 MappingJackson2HttpMessageConverter，而是替换它内部使用的 ObjectMapper。

### 4. WebMvcConfiguration 的作用

通过：

```java
protected void extendMessageConverters(
        List<HttpMessageConverter<?>> converters)
```

Spring MVC 提供已经准备好的消息转换器列表。

当前项目的配置：

1. 遍历现有转换器。
2. 找到 MappingJackson2HttpMessageConverter。
3. 将内部 ObjectMapper 替换为 JacksonObjectMapper。
4. 如果没有找到 JSON 转换器，则创建并添加一个。

最终其他转换器不受影响，JSON 转换器使用项目自定义的日期格式。

---

## 六、Java 面向对象设计启发

本次代码还涉及三个重要概念：

### 1. 继承

```java
JacksonObjectMapper extends ObjectMapper
```

子类继承父类能力，并可以扩展配置。

### 2. 多态

子类对象可以作为父类类型使用：

```java
converter.setObjectMapper(new JacksonObjectMapper());
```

### 3. 组合

MappingJackson2HttpMessageConverter 内部持有 ObjectMapper，通过 setObjectMapper() 配置内部依赖。

**工程启发：扩展现有组件不一定要修改原组件源码，也不一定要创建大量子类。继承、组合和模块注册都可以用于扩展功能。**

如果只是增加一种字段转换规则，通常只需要增加对应的 Serializer / Deserializer 并注册到现有 Module。

如果规则越来越多，可以拆分多个 Module；不同业务需要不同 JSON 规范时，再考虑独立 ObjectMapper。

---

## 七、本次工程验收

### 已完成

- [x] 员工分页接口实现。
- [x] 姓名模糊搜索。
- [x] 创建时间倒序。
- [x] PageHelper 分页查询。
- [x] PageInfo 提取分页信息。
- [x] Employee 转换为 EmployeeVO。
- [x] 排除 password 字段。
- [x] JacksonObjectMapper 注册。
- [x] 统一日期时间 JSON 格式。
- [x] JDK 17 下 Maven 编译通过。
- [x] Swagger 与数据库联调通过。

### 当前掌握的核心能力

1. 理解 PageHelper 的分页拦截机制。
2. 理解 Page、PageInfo、PageResult 的区别。
3. 理解 DTO、Entity、VO 的职责划分。
4. 理解 Spring MVC 的 HTTP 消息转换机制。
5. 理解 Jackson 的序列化和反序列化。
6. 理解通过继承、组合和模块注册扩展现有组件。

### 本阶段工程认识

后端开发不仅是实现 SQL 和 CRUD，还需要考虑：

- 数据库实体是否适合直接返回前端？
- 分页信息应该由谁封装？
- 日期格式应该由每个字段单独管理，还是全局统一管理？
- 如何在不修改框架原有实现的情况下扩展功能？

这些都是后端工程设计的一部分。