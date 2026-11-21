package com.sky.aspect;

import com.sky.annotation.AutoFill;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Aspect
@Component
public class AutoFillAspect {
    @Pointcut("execution(* com.sky.mapper..*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void autoFillMethods() { }

    @Before("autoFillMethods()")
    public void fill(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        AutoFill annotation = method.getAnnotation(AutoFill.class);
        Object[] args = joinPoint.getArgs();
        if (annotation == null || args.length == 0 || args[0] == null) {
            throw new IllegalStateException("AutoFill 要求 Mapper 方法的第一个参数为非空实体: " + method);
        }
        Long currentId = BaseContext.getCurrentId();
        if (currentId == null) throw new IllegalStateException("AutoFill 无法获取当前登录员工 ID");
        Object entity = args[0];
        LocalDateTime now = LocalDateTime.now();
        try {
            // 先查找全部必需的 setter，再赋值，避免缺少方法时只填充了一部分字段。
            Method updateTime = entity.getClass().getMethod("setUpdateTime", LocalDateTime.class);
            Method updateUser = entity.getClass().getMethod("setUpdateUser", Long.class);
            Method createTime = null;
            Method createUser = null;
            if (annotation.value() == OperationType.INSERT) {
                createTime = entity.getClass().getMethod("setCreateTime", LocalDateTime.class);
                createUser = entity.getClass().getMethod("setCreateUser", Long.class);
            }
            updateTime.invoke(entity, now);
            updateUser.invoke(entity, currentId);
            if (createTime != null) {
                createTime.invoke(entity, now);
                createUser.invoke(entity, currentId);
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("AutoFill 实体缺少必需的 setter 或调用失败: " + entity.getClass().getName(), ex);
        }
    }
}
