Cache Aside（旁路缓存）模式：查询时先查 Redis，未命中再查 MySQL 并回填缓存；更新时先提交数据库，再删除缓存。



Redis String + JSON 序列化：使用 dish_{categoryId} 作为 Key，将 List<DishVO> 序列化成 JSON 字符串存储，读取时反序列化。缓存整个分类的列表，适合当前商品浏览场景。



精确缓存失效：新增、删除、起售停售时清理对应分类；修改菜品分类时同时清理旧分类和新分类。已知 Key 就直接 DEL，无需使用 KEYS 或 SCAN。



TTL 与异常降级：当前 TTL 为 30 分钟；Redis 读取失败时可以降级查询 MySQL；删除失败时旧缓存可能继续存在，TTL 是兜底而不是强一致性保证。