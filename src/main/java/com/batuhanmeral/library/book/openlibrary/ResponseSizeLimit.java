package com.batuhanmeral.library.book.openlibrary;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Belli boyuttan büyük cevap gövdesini reddeder. Gövde okunurken sayılır, yanlış Content-Length işe
 * yaramaz.
 */
final class ResponseSizeLimit implements ClientHttpRequestInterceptor {

    private final long maxBytes;

    ResponseSizeLimit(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        if (response.getHeaders().getContentLength() > maxBytes) {
            response.close();
            throw new IOException("Response body larger than " + maxBytes + " bytes");
        }
        return new LimitedResponse(response, maxBytes);
    }

    private record LimitedResponse(ClientHttpResponse delegate, long maxBytes)
            implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() throws IOException {
            return new LimitedInputStream(delegate.getBody(), maxBytes);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {

        private final long maxBytes;
        private long read;

        LimitedInputStream(InputStream in, long maxBytes) {
            super(in);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws IOException {
            read += n;
            if (read > maxBytes) {
                throw new IOException("Response body larger than " + maxBytes + " bytes");
            }
        }
    }
}
