package com.orbit.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

final class JsonErrors {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private JsonErrors() {}
    static void write(HttpServletResponse response,int status,String title,String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        JSON.writeValue(response.getWriter(),Map.of("status",status,"title",title,"detail",detail));
    }
}
