# HTTP Response 与 Excel 文件下载机制

> TASK07 — 苍穹外卖
>
> 核心知识：HttpServletResponse、ServletOutputStream、POI、HTTP 文件下载、缓冲区与流式传输。

## 一、HttpServletRequest 和 HttpServletResponse

每次 HTTP 请求到达服务器后，Servlet 容器（例如 Tomcat）会为本次请求提供对应的 Request 和 Response 对象。

- `HttpServletRequest`：封装客户端发送过来的请求信息。
- `HttpServletResponse`：代表服务器准备返回给客户端的 HTTP 响应。

Spring MVC 在调用 Controller 时，会通过参数解析机制提供对应的 Request 和 Response 对象。

例如：

```java
@GetMapping("/export")
public void export(HttpServletResponse response) {
    reportService.exportBusinessData(response);
}
```

这里不需要自己创建 Response，Spring MVC 会提供当前请求对应的响应对象。

注意：

Controller 本身通常是 Spring IOC 容器中的单例对象，而 Request 和 Response 是每次请求各自对应的对象，不是 Controller 的成员变量。

## 二、普通 JSON 接口的响应流程

之前常见的 Controller：

```java
@GetMapping("/list")
public Result<List<DishVO>> list() {
    List<DishVO> list = dishService.list();
    return Result.success(list);
}
```

执行流程：

1. 浏览器发送 HTTP Request。
2. Tomcat 接收请求，提供 Request 和 Response。
3. Spring MVC 调用对应的 Controller。
4. Controller 调用 Service，获取业务数据。
5. Controller 返回 Result 对象。
6. Spring MVC 接收返回值，使用 Jackson 等消息转换器将 Java 对象序列化为 JSON。
7. Spring MVC 将 JSON 数据写入 Response。
8. Tomcat 负责完成 HTTP 响应并发送给浏览器。

核心理解：

**普通 JSON 接口中，Controller 只需要返回 Java 对象，Spring MVC 会自动完成序列化和 Response 写入。**

## 三、Excel 文件下载的响应流程

Excel 下载采用另一种方式：Controller/Service 直接操作 Response，而不是返回一个 Result 对象。

示例：

```java
public void exportBusinessData(HttpServletResponse response) {

    // 1. 查询业务数据
    BusinessDataVO data = workspaceService.getBusinessData(...);

    // 2. 读取 Excel 模板
    InputStream in = getClass()
        .getClassLoader()
        .getResourceAsStream("template/运营数据报表模板.xlsx");

    // 3. 创建 Excel 工作簿
    XSSFWorkbook excel = new XSSFWorkbook(in);

    // 4. 填充 Excel 数据
    // ...

    // 5. 获取 HTTP 响应输出流
    ServletOutputStream out = response.getOutputStream();

    // 6. 写入 Excel 文件
    excel.write(out);

    // 7. 刷新输出流
    out.flush();
}
```

### 1. 为什么可以直接下载？

关键代码：

```java
ServletOutputStream out = response.getOutputStream();
excel.write(out);
```

`response.getOutputStream()` 获取的是当前 HTTP 响应的输出流。

它不是普通的本地文件输出流，而是与当前 HTTP Response 关联的 Servlet 输出流。

`excel.write(out)` 的工作：

1. POI 将内存中的 Excel 工作簿转换成 `.xlsx` 格式的二进制数据。
2. 将生成的字节写入 ServletOutputStream。
3. 输出流将数据交给 Servlet 容器管理的响应机制。
4. Tomcat 负责完成 HTTP 响应传输。
5. 浏览器接收文件数据并处理下载。

数据流：

```text
Excel 工作簿（内存）
       |
       | excel.write(out)
       v
ServletOutputStream
       |
       v
HTTP Response 响应体
       |
       v
Tomcat
       |
       v
网络传输
       |
       v
浏览器接收文件
```

**关键理解：**

`excel.write(out)` 不会直接操作用户电脑，也不代表 POI 自己负责网络传输。

它只是将 Excel 文件的二进制数据写入 HTTP Response 的输出流。

最终的 HTTP 网络传输仍然由 Tomcat 负责。

### 2. 为什么还需要设置响应头？

浏览器需要知道服务器返回的是什么文件。

通常设置：

```java
response.setContentType(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
);

response.setHeader(
    "Content-Disposition",
    "attachment; filename=business.xlsx"
);
```

- `Content-Type`：告诉浏览器响应体的数据类型。
- `Content-Disposition`：告诉浏览器以附件形式处理，建议下载文件。

如果前端使用 Axios 下载，还需要设置 `responseType: 'blob'`。

### 3. JSON 和 Excel 下载的区别

| 对比 | JSON 接口 | Excel 下载 |
|---|---|---|
| Controller 返回 | Result 对象 | 通常为 void |
| 内容生成 | Java 对象 | Excel 工作簿 |
| 数据转换 | Spring MVC/Jackson | Apache POI |
| Response 写入 | Spring MVC 自动完成 | Controller/Service 直接写入 |
| 最终发送 | Tomcat | Tomcat |
| 浏览器接收 | JSON | Excel 文件 |

注意：两者底层都需要将数据写入 HTTP Response，只是由谁完成写入工作的区别。

## 四、write、flush、close 的区别

### 1. write()

```java
excel.write(out);
```

将 Excel 工作簿的二进制数据写入输出流。

注意：write 完成不代表浏览器已经全部收到文件。

### 2. flush()

```java
out.flush();
```

刷新输出缓冲区，将当前缓冲的数据向下游输出。

特点：

- 推动缓冲区中的数据输出。
- 不会关闭输出流。
- 后续仍然可以继续写入。
- 不保证数据已经全部到达浏览器。

另外，flush 可能导致 HTTP Response 被提交（Committed），之后响应头和状态码通常不能再随意修改。

### 3. close()

```java
out.close();
```

关闭输出流。

特点：

- 通常会先刷新缓冲区。
- 关闭输出流。
- 后续不能再继续写入。

close 不等于 Java 请求处理线程立即结束。

ServletOutputStream 通常由 Servlet 容器管理，业务代码一般不需要主动关闭它，容器会在请求处理结束时负责关闭。

### 4. 资源管理建议

- `XSSFWorkbook`：自己创建的资源，应该关闭。
- `InputStream`：自己创建的资源，应该关闭。
- `ServletOutputStream`：由容器提供，通常交给容器管理。

可以使用 try-with-resources 管理 Workbook 和模板输入流，同时正常 flush 响应输出流。

## 五、大文件传输与缓冲区机制

一个重要问题：

如果 Excel 文件有 100 MB，是否必须先将 100 MB 全部放进 Response 缓冲区，再一次性发送？

不是。

### 1. 数据如何传输？

大文件可以通过流式写入和分段传输：

```text
生成文件数据
     |
     v
ServletOutputStream
     |
     v
响应缓冲区
     |
     v
Tomcat 向下游输出
     |
     v
网络分段传输
     |
     v
浏览器逐步接收
```

HTTP 层面仍然是一次完整的 HTTP Response，但底层数据可以分多次写入和传输。

### 2. 浏览器下载进度

假设文件大小为 10 MB，浏览器已经接收 5 MB：

\[
Progress = \frac{5}{10}\times100\%=50\%
\]

如果服务器通过 `Content-Length` 提供了文件总大小，浏览器就可以计算下载百分比。

如果不知道总大小，可能只能显示不确定进度。

注意：

- 浏览器直接下载时，可以使用浏览器自己的下载管理器。
- Axios 下载时，可以通过下载进度事件实现前端进度展示。

### 3. 普通 JSON 也可以分段传输

不能简单认为：

- JSON 一定是整个对象打包后一次性发送。
- Excel 一定是分段发送。

普通 JSON 最终也会经过输出流和缓冲区。

两者真正的区别在于数据如何生成、序列化和写入，而不是 HTTP 传输机制完全不同。

## 六、POI：XSSFWorkbook 与 SXSSFWorkbook

### XSSFWorkbook

```java
XSSFWorkbook excel = new XSSFWorkbook(in);
```

用于操作 `.xlsx` 格式的 Excel 文件。

特点：

- 可以读取已有 Excel 模板。
- 可以修改单元格内容、样式和格式。
- 工作簿数据主要维护在内存中。

适合普通报表导出。

### SXSSFWorkbook

```java
SXSSFWorkbook excel = new SXSSFWorkbook();
```

用于大型 Excel 的流式写入。

特点：

- 采用流式写入机制。
- 可以减少大型 Excel 导出时的内存占用。
- 通常借助临时文件保存已经写出的行。
- 适合大规模数据导出。

注意：

HTTP 流式传输不代表 Excel 生成过程一定不占用大量内存。

例如 XSSFWorkbook 即使通过 ServletOutputStream 分段输出，仍然可能在内存中维护整个工作簿。

因此需要区分：

- HTTP 传输的流式机制。
- Excel 文件生成的内存管理机制。

## 七、Excel 模板与跨平台部署

实际业务中，经常提前准备 Excel 模板，而不是每次都使用 POI 从零创建所有样式。

原因：

- 可以保留原有字体、边框、颜色。
- 可以保留合并单元格和布局。
- 可以预设公式和图表。
- Java 只需要负责填充数据。

推荐将模板放在：

```text
src/main/resources/
    template/
        运营数据报表模板.xlsx
```

通过 ClassLoader 读取：

```java
InputStream in = getClass()
    .getClassLoader()
    .getResourceAsStream("template/运营数据报表模板.xlsx");
```

Maven 打包时会将资源放入应用程序的 Classpath，因此部署到 Linux 服务器后仍然可以读取。

对比：

| 读取方式 | Windows | Linux |
|---|---|---|
| Classpath 资源读取 | 支持 | 支持 |
| Windows 绝对路径 | 支持 | 不支持 |
| Linux 绝对路径 | 不支持 | 支持 |

注意 Linux 文件名区分大小写，资源路径必须准确。

## 八、知识点总结

### HTTP 响应流程

1. Tomcat 提供 Request 和 Response。
2. Spring MVC 将对应对象提供给 Controller。
3. 普通 JSON：Controller 返回 Java 对象，Spring MVC 自动序列化并写入 Response。
4. 文件下载：Controller/Service 直接获取 Response 输出流并写入文件数据。
5. Tomcat 负责完成 HTTP 响应传输。

### 文件下载核心 API

| API | 作用 |
|---|---|
| `response.setContentType()` | 设置响应内容类型 |
| `response.setHeader()` | 设置响应头 |
| `response.getOutputStream()` | 获取响应输出流 |
| `excel.write(out)` | 将 Excel 二进制数据写入响应流 |
| `out.flush()` | 刷新输出缓冲区 |
| `out.close()` | 关闭输出流 |

### 最重要的三个理解

1. Response 是 HTTP 响应容器，可以放 JSON、Excel、PDF、图片等不同类型的响应内容。
2. `excel.write(out)` 是后端将文件数据写入 HTTP 响应流，不是直接操作网络连接。
3. Tomcat 负责 HTTP 响应传输，文件可以逐步写入和传输，不需要整个文件先进入一个响应缓冲区。

---

**本模块学习收获：**

通过苍穹外卖运营数据报表导出，理解了 Spring MVC 自动 JSON 响应与手动文件响应的区别，掌握了 HttpServletResponse、ServletOutputStream、POI 模板填充、缓冲区刷新及文件流式传输的基本原理。