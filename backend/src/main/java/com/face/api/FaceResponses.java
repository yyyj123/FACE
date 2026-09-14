package com.face.api;

import java.util.LinkedHashMap;
import java.util.Map;

final class FaceResponses {
    private FaceResponses() {
    }

    static Map<String, Object> ok(Object data) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", 0);
        body.put("msg", "success");
        body.put("data", data);
        return body;
    }

    static Map<String, Object> error(int code, String message) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", code);
        body.put("msg", message);
        return body;
    }
}
