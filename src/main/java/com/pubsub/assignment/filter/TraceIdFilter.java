package com.pubsub.assignment.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Puts a {@code traceId} on the MDC for the lifetime of the request, including asynchronous
 * (Servlet 3.0 async) request handling. Since {@link OncePerRequestFilter} by default skips async
 * dispatches, this filter opts back into being invoked for them so the trace id (stashed as a
 * request attribute) can be restored into MDC on whichever thread services the async dispatch. MDC
 * is only cleared once the request is no longer in an async-started state, i.e. once the entire
 * request/response lifecycle - including any async continuation - has actually completed.
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String GCP_TRACE_HEADER = "X-Cloud-Trace-Context";
    private static final String TRACE_ID_ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID_KEY, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (!request.isAsyncStarted()) {
                MDC.remove(TRACE_ID_KEY);
            }
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        Object existing = request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (existing != null) {
            return (String) existing;
        }

        String header = request.getHeader(GCP_TRACE_HEADER);
        String traceId = (header == null || header.isBlank()) ? UUID.randomUUID().toString() : header.split("/")[0];
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        return traceId;
    }
}