package com.face.platform.v3.api;

import org.springframework.http.HttpStatus;

public class V3ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public V3ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
