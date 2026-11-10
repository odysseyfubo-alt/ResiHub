package com.resihub.config;

import com.resihub.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error("服务器异常: {}", e.getMessage(), e);
        return Result.fail(e.getMessage() == null ? "服务器异常" : e.getMessage());
    }
}
