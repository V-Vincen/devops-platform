package com.vincent.devops.common;

/**
 * 平台统一接口响应结构。
 *
 * @param success 是否成功
 * @param data    业务数据
 * @param message 面向调用方的简短提示
 */
public record ApiResponse<T>(boolean success, T data, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "操作成功");
    }

    public static <T> ApiResponse<T> failed(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
