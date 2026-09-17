package com.liuqitech.accountingassistant.exception;

/**
 * 资源不存在异常（HTTP 404）。用于账本/成员等资源未找到的场景。
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
