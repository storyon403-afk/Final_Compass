package cn.finalscompass.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Proxies validated attachments to the isolated MarkItDown worker. */
@Service
public class AiDocumentConversionService {
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "docx", "pptx", "xls", "xlsx", "txt", "md", "csv", "json", "xml", "html", "htm",
            "png", "jpg", "jpeg", "webp", "wav", "mp3", "m4a");
    private final URI workerUri;
    private final String workerToken;
    private final HttpClient http;
    private final ObjectMapper json;
    private final Duration requestTimeout;

    public AiDocumentConversionService(
            @Value("${app.ai.document-worker.url:}") String workerUrl,
            @Value("${app.ai.document-worker.token:}") String workerToken,
            @Value("${app.ai.document-worker.timeout:60s}") Duration timeout,
            ObjectMapper json) {
        this.workerUri = workerUrl == null || workerUrl.isBlank() ? null : URI.create(workerUrl.replaceAll("/+$", "") + "/convert");
        this.workerToken = workerToken == null ? "" : workerToken;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build();
        this.json = json;
        this.requestTimeout = timeout;
    }

    public ConversionResult convert(MultipartFile file) {
        if (workerUri == null || workerToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "附件解析服务尚未配置");
        }
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("附件不能为空");
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "单个附件不能超过 20MB");
        String originalName = safeFileName(file.getOriginalFilename());
        String extension = extension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) throw new IllegalArgumentException("暂不支持此附件类型");

        try {
            String boundary = "----FinalsCompass" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = multipartBody(boundary, extension, safeContentType(file.getContentType()), file.getBytes());
            HttpRequest request = HttpRequest.newBuilder(workerUri)
                    .timeout(requestTimeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("X-Worker-Token", workerToken)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        response.statusCode() == 413 ? "附件超过解析服务限制" : "附件解析失败，请检查文件内容");
            }
            WorkerResponse converted = json.readValue(response.body(), WorkerResponse.class);
            return new ConversionResult(originalName, converted.contentType(), converted.markdown(),
                    converted.characters(), converted.truncated());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "附件解析被中断");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "附件解析服务暂不可用");
        }
    }

    private byte[] multipartBody(String boundary, String extension, String contentType, byte[] data) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(data.length + 512);
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"file\"; filename=\"attachment." + extension + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + (contentType == null ? "application/octet-stream" : contentType) + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        output.write(data);
        output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private String safeFileName(String value) {
        String name = value == null ? "attachment" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > 180) throw new IllegalArgumentException("附件名称无效或过长");
        return name;
    }

    private String extension(String name) {
        int index = name.lastIndexOf('.');
        if (index < 1 || index == name.length() - 1) throw new IllegalArgumentException("附件缺少有效扩展名");
        return name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String safeContentType(String value) {
        return value != null && value.matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+")
                ? value : "application/octet-stream";
    }

    public record ConversionResult(String fileName, String contentType, String markdown,
                                   int characters, boolean truncated) {}
    private record WorkerResponse(String fileName, String contentType, String markdown,
                                  int characters, boolean truncated) {}
}
