# TASK07 苍穹外卖 — 微信小程序登录模块学习笔记

**项目：** 苍穹外卖（Sky Takeout）  
**技术栈：** Spring Boot、MyBatis、MySQL、Apache HttpClient、JWT、微信小程序  
**模块状态：** 登录主链路联调 PASS；用户端 JWT 拦截器待后续业务接口验证

## 一、模块目标

实现微信小程序用户登录，并将微信身份映射到苍穹外卖自己的用户系统。

核心思路：

**微信负责身份验证，MySQL 负责管理本地用户，JWT 负责后续登录状态。**

## 二、完整登录流程

```text
微信小程序
    |
    | uni.login() 获取临时 code
    |
    | POST /user/user/login
    | Body: {"code": "..."}
    v
UserController
    |
    | @RequestBody UserLoginDTO
    v
UserServiceImpl
    |
    | 校验 code
    v
WeChatLoginClient
    |
    | Apache HttpClient
    | GET 微信 jscode2session 接口
    | 参数：appid、secret、js_code、grant_type
    v
微信服务器
    |
    | 验证 code，返回 openid
    v
UserServiceImpl
    |
    | UserMapper.getByOpenid(openid)
    v
MySQL user 表
    |
    | 已存在：查询用户
    | 不存在：创建用户并获取数据库主键
    v
JwtUtil.createJWT()
    |
    | 将本地 userId 写入 JWT Claims
    v
UserLoginVO(id, openid, token)
    |
    v
Result.success()
    |
    v
微信小程序接收并保存 token
```

## 三、各层职责

| 组件 | 职责 |
|---|---|
| `UserController` | 接收 HTTP 请求，调用 Service，返回统一响应 |
| `UserServiceImpl` | 执行用户查询、注册及 JWT 签发 |
| `WeChatLoginClient` | 封装微信接口调用及响应解析 |
| `UserMapper` | 查询或插入 MySQL 用户 |
| `JwtUtil` | 根据用户 ID、密钥和有效期生成 JWT |
| `JwtTokenUserInterceptor` | 后续请求验证 JWT 并识别当前用户 |

**设计原则：** Controller 处理接口，Service 处理业务，Client 调用外部服务，Mapper 操作数据库。

## 四、Spring 依赖注入

本模块采用构造器注入：

```java
private final UserService userService;

public UserController(UserService userService) {
    this.userService = userService;
}
```

与字段注入 `@Autowired` 的本质相同，都是由 Spring IoC 容器提供依赖对象。

区别：

- 字段注入：对象构造后注入成员变量。
- 构造器注入：创建对象时通过构造参数提供依赖。
- Bean 只有一个构造器时，Spring 可以省略 `@Autowired`。
- 构造器注入支持 `final`，依赖更明确，也方便单元测试。

**以后优先采用构造器注入。**

## 五、WeChatLoginClient 核心知识

### 1. 构造请求

使用 `URIBuilder` 拼接微信接口地址及参数。

微信登录接口：

`https://api.weixin.qq.com/sns/jscode2session`

主要参数：

- `appid`：小程序标识。
- `secret`：小程序服务端密钥。
- `js_code`：小程序获取的临时 code。
- `grant_type`：`authorization_code`。

### 2. 发送请求

使用 Apache HttpClient：

- `HttpClients.createDefault()`：创建客户端。
- `new HttpGet(uri)`：创建 GET 请求。
- `client.execute(request)`：真正发送请求。
- `EntityUtils.toString(...)`：读取响应体。
- `try-with-resources`：自动关闭资源。

当前实现设置了连接、连接池等待和读取超时，避免请求无限等待。

### 3. 解析响应

使用 Fastjson 将响应 JSON 转为 `JSONObject`，读取 `openid` 和 `errcode`。

**HTTP 200 不代表微信登录成功。**

例如微信返回 `errcode=40029`，说明 code 无效。即使 HTTP 状态为 200，也应判定业务失败并抛出异常。

一旦 `WeChatLoginClient` 抛出异常，Service 不会继续执行后面的 MySQL 查询与 JWT 签发。

## 六、code、openid、userId、JWT 的区别

| 信息 | 来源 | 作用 |
|---|---|---|
| `code` | 微信登录接口 | 短时、一次性的登录凭证 |
| `openid` | 微信服务器 | 标识某个小程序下的微信用户 |
| `userId` | 本地 MySQL | 标识苍穹外卖系统中的用户 |
| `JWT` | Java 后端 | 后续请求的登录凭证 |

**为什么不让前端直接传 openid？**

因为 openid 是身份标识，不是身份证明。后端直接相信客户端提供的 openid，可能导致冒用身份。

而且调用微信 `jscode2session` 需要 AppSecret。这个密钥不能放到小程序客户端，否则可能被提取。

因此由小程序获取 code，后端持有 AppSecret 并向微信验证 code，再获取可信的 openid。

**openid 会不会变化？**

同一个微信用户在同一个小程序 AppID 下，openid 通常保持稳定；不同小程序的 openid 通常不同。不能把它理解为跨所有微信应用永久通用的用户 ID。

## 七、登录异常与重试

典型异常：小程序获得 code 后，Java 后端宕机。

- code 有有效期，等待过久会失效。
- code 通常只能成功兑换一次。
- 如果后端已向微信兑换成功，但响应在返回小程序途中丢失，旧 code 也可能无法再次使用。
- 重新登录时应重新调用 `uni.login()` 获取新 code，而不是无限重试旧 code。

**code 每次可能不同，但同一用户的 openid 通常稳定，因此重新登录仍能找到原来的 MySQL 用户。**

## 八、JWT 与后续业务请求

登录成功后，Java 后端将本地 `userId` 放入 JWT Claims，使用用户端密钥签名并返回 token。

后续请求预期流程：

`小程序携带 authentication 请求头 → JWT 拦截器验证签名及有效期 → 提取 userId → BaseContext 保存当前用户 ID → Controller/Service 执行业务`

请求结束后应清理线程中的用户上下文，避免线程复用引发身份混淆。

后续访问购物车、订单、地址簿时，正常情况下不必每次重新请求微信服务器。

## 九、实际联调结果

已完成：

- [x] 微信开发者工具成功发送 `POST /user/user/login`。
- [x] 后端成功返回 `id`、`openid`、`token`。
- [x] MySQL `user` 表存在对应用户。
- [x] 完成 Controller、Service、WeChatLoginClient 核心代码学习。
- [ ] 使用受保护的用户端接口验证 JWT 拦截器。
- [ ] 验证登录失效、重新登录等异常场景（后续按需进行）。

## 十、总结

**微信登录不是简单地获取 openid，而是建立一条可信的身份验证链。**

`code → 微信验证 → openid → 本地 userId → JWT`

其中：

- 微信负责验证外部身份。
- 后端负责建立本地用户身份。
- JWT 负责后续请求认证。
- Spring IoC 负责组件依赖管理。
- Client、Service、Mapper 分别承担通信、业务和持久化职责。

这是微信小程序后端开发中非常典型的一套登录架构。