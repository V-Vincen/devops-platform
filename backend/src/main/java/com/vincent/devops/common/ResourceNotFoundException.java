package com.vincent.devops.common;

/**
 * 表示请求的业务资源不存在。
 *
 * <p>统一转换为 404 响应，避免在控制器中重复编写异常处理逻辑。</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
