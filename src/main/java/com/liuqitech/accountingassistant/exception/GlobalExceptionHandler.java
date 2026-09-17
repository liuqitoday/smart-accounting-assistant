package com.liuqitech.accountingassistant.exception;

import com.liuqitech.accountingassistant.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 业务异常处理
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        logger.warn("业务异常: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), e.getErrorCode()));
    }

    /**
     * 权限不足异常处理（账本角色不足/非成员）
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        logger.warn("权限不足: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage(), "FORBIDDEN"));
    }

    /**
     * 资源不存在异常处理
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException e) {
        logger.warn("资源不存在: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage(), "NOT_FOUND"));
    }
    
    /**
     * 静态资源不存在（如 iOS 自动探测 apple-touch-icon、favicon 等）。
     * 这类缺失属正常 404，不打堆栈，避免污染 ERROR 日志。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        logger.debug("静态资源不存在: {}", e.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    /**
     * 参数验证异常处理
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException e) {
        
        logger.warn("参数验证失败: {}", e.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("参数验证失败", "VALIDATION_ERROR"));
    }
    
    /**
     * 绑定异常处理
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleBindException(BindException e) {
        logger.warn("参数绑定失败: {}", e.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("参数绑定失败", "BIND_ERROR"));
    }
    
    /**
     * 路径/查询参数类型不匹配（如 /api/transactions/abc 的 id 非数字）。
     * 属客户端请求错误，返回 400；不专门处理会落进 RuntimeException 兜底变成 500。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        logger.warn("参数类型不匹配: {} = {}", e.getName(), e.getValue());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("参数不合法，请检查输入", "ILLEGAL_ARGUMENT"));
    }

    /**
     * 非法参数异常处理。
     * 安全约束：IAE 也可能由框架/JDK 抛出（Spring Assert、Enum.valueOf 带全限定类名、
     * NumberFormatException 等），message 可能含内部实现细节，不回显给客户端；
     * 业务侧需要用户友好提示的场景应抛 BusinessException（可带 ILLEGAL_ARGUMENT 码）。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.warn("非法参数异常: {}", e.getMessage(), e);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("参数不合法，请检查输入", "ILLEGAL_ARGUMENT"));
    }
    
    /**
     * 空指针异常处理
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ApiResponse<Void>> handleNullPointerException(NullPointerException e) {
        logger.error("空指针异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("系统内部错误", "NULL_POINTER"));
    }
    
    /**
     * 运行时异常处理。
     * 安全约束：真实 message 可能携带 SQL/路径/上游响应等内部信息，只进日志不回显。
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException e) {
        logger.error("运行时异常: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("系统繁忙，请稍后重试", "RUNTIME_ERROR"));
    }
    
    /**
     * 通用异常处理
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        logger.error("未知异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("系统内部错误", "INTERNAL_ERROR"));
    }
}
