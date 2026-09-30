package com.orbit.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Limits both declared and chunked API payloads before deserialization. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestBodyLimitFilter extends OncePerRequestFilter {
    static final int MAX_BYTES=65536;
    public static final class PayloadTooLargeException extends IOException {
        PayloadTooLargeException() { super("Request body exceeds 64 KiB."); }
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if (!request.getRequestURI().startsWith(request.getContextPath()+"/api/")) { chain.doFilter(request,response); return; }
        if (request.getContentLengthLong() > MAX_BYTES) {
            JsonErrors.write(response,413,"Payload too large","Request body must not exceed 64 KiB."); return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            private ServletInputStream limited;
            @Override public ServletInputStream getInputStream() throws IOException {
                if (limited != null) return limited;
                ServletInputStream source=super.getInputStream();
                limited=new ServletInputStream() {
                    private long count;
                    private int account(int read) throws IOException { if (read > 0 && (count+=read)>MAX_BYTES) throw new PayloadTooLargeException(); return read; }
                    @Override public int read() throws IOException { int value=source.read(); if(value>=0) account(1); return value; }
                    @Override public int read(byte[] bytes,int offset,int length) throws IOException { return account(source.read(bytes,offset,Math.min(length,MAX_BYTES+1-(int)count))); }
                    @Override public boolean isFinished() { return source.isFinished(); }
                    @Override public boolean isReady() { return source.isReady(); }
                    @Override public void setReadListener(ReadListener listener) { source.setReadListener(listener); }
                    @Override public void close() throws IOException { source.close(); }
                }; return limited;
            }
            @Override public BufferedReader getReader() throws IOException {
                return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));
            }
        },response);
    }
}
