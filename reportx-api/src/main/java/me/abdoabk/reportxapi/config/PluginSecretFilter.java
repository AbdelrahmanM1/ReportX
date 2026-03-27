package me.abdoabk.reportxapi.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Rejects requests to /api/internal/** that don't carry the correct X-Plugin-Secret header.
 */
public class PluginSecretFilter implements Filter {

    private static final String HEADER = "X-Plugin-Secret";
    private final String expectedSecret;

    public PluginSecretFilter(String expectedSecret) {
        this.expectedSecret = expectedSecret;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;

        if (req.getRequestURI().startsWith("/api/internal/")) {
            String secret = req.getHeader(HEADER);
            if (secret == null || !secret.equals(expectedSecret)) {
                ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid plugin secret");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
