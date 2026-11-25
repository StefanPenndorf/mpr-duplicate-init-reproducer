package repro;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

/**
 * Only active with {@code -Drepro.resendFirstInit=true}. Simulates a legacy init request whose
 * response is lost on the way to the browser and that is therefore sent again by the browser or
 * by a proxy: the server handles the request twice, the browser only sees the second response.
 */
@Component
public class ResendFirstInitRequestFilter extends HttpFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ResendFirstInitRequestFilter.class);

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (Boolean.getBoolean("repro.resendFirstInit") && isLegacyInitRequest(request)) {
            LOG.warn("REPRO: handling the legacy init request once, discarding the response");
            chain.doFilter(request, new DiscardingResponse(response));
            LOG.warn("REPRO: handling the same legacy init request again");
        }
        chain.doFilter(request, response);
    }

    private static boolean isLegacyInitRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && request.getRequestURI().contains("/framework")
                && request.getParameter("v-browserDetails") != null;
    }

    private static class DiscardingResponse extends HttpServletResponseWrapper {

        DiscardingResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public void write(int b) {
                    // discarded
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                    // not needed
                }
            };
        }

        @Override
        public PrintWriter getWriter() {
            return new PrintWriter(Writer.nullWriter());
        }

        @Override
        public void setContentLength(int len) {
            // discarded
        }

        @Override
        public void setContentLengthLong(long len) {
            // discarded
        }

        @Override
        public void setStatus(int sc) {
            // discarded
        }

        @Override
        public void sendError(int sc) {
            // discarded
        }

        @Override
        public void sendError(int sc, String msg) {
            // discarded
        }

        @Override
        public void flushBuffer() {
            // discarded
        }
    }
}
