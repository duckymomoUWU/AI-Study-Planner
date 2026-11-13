package com.studyplanner.recall;

import java.io.*;
import java.lang.reflect.Type;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Apply the shared strict-input contract locally without changing older endpoints. */
@ControllerAdvice(basePackages="com.studyplanner.recall")
public class RecallRequestAdvice extends RequestBodyAdviceAdapter {
    private final ObjectMapper strict;
    public RecallRequestAdvice(ObjectMapper mapper) {
        strict=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        strict.coercionConfigFor(LogicalType.Integer).setCoercion(CoercionInputShape.String,CoercionAction.Fail);
    }
    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return parameter.getContainingClass().getPackageName().equals("com.studyplanner.recall");
    }
    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type,
            Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        byte[] bytes=input.getBody().readAllBytes();
        try {strict.readerFor(strict.constructType(type)).readValue(bytes);}
        catch (IOException e) {throw RecallException.validation("body","invalid_body","Dữ liệu không hợp lệ hoặc có trường không được phép.");}
        return new HttpInputMessage() {
            public InputStream getBody() {return new ByteArrayInputStream(bytes);}
            public HttpHeaders getHeaders() {return input.getHeaders();}
        };
    }
}
