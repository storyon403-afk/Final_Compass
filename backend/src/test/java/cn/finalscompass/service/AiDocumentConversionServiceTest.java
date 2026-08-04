package cn.finalscompass.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiDocumentConversionServiceTest {
    @Test
    void disabledWorkerFailsClosed() {
        var service = new AiDocumentConversionService("", "", Duration.ofSeconds(1), new ObjectMapper());
        var file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.convert(file));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void rejectsUnsupportedExtensionBeforeCallingWorker() {
        var service = new AiDocumentConversionService(
                "http://127.0.0.1:9", "test-token", Duration.ofMillis(100), new ObjectMapper());
        var file = new MockMultipartFile("file", "payload.exe", "application/octet-stream", new byte[]{1});

        assertThrows(IllegalArgumentException.class, () -> service.convert(file));
    }

    @Test
    void proxiesMultipartToWorkerWithTokenAndReadsMarkdown() throws Exception {
        AtomicReference<String> requestProtocol = new AtomicReference<>();
        AtomicReference<String> workerToken = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer worker = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        worker.createContext("/convert", exchange -> {
            requestProtocol.set(exchange.getProtocol());
            workerToken.set(exchange.getRequestHeaders().getFirst("X-Worker-Token"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"markdown\":\"# Parsed\\n\\n内容\",\"characters\":11,\"truncated\":false}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        worker.start();

        try {
            var service = new AiDocumentConversionService(
                    "http://127.0.0.1:" + worker.getAddress().getPort(),
                    "worker-secret", Duration.ofSeconds(2), new ObjectMapper());
            var file = new MockMultipartFile(
                    "file", "概率论.md", "text/markdown", "中心极限定理".getBytes(StandardCharsets.UTF_8));

            AiDocumentConversionService.ConversionResult result = service.convert(file);

            assertEquals("HTTP/1.1", requestProtocol.get());
            assertEquals("worker-secret", workerToken.get());
            // The internal boundary intentionally receives a neutral filename;
            // the original user filename is only returned by the Java layer.
            assertTrue(requestBody.get().contains("filename=\"attachment.md\""));
            assertTrue(requestBody.get().contains("中心极限定理"));
            assertEquals("# Parsed\n\n内容", result.markdown());
            assertEquals(11, result.characters());
        } finally {
            worker.stop(0);
        }
    }
}
