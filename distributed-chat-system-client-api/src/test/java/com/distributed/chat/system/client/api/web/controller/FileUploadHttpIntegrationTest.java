package com.distributed.chat.system.client.api.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 실제 Tomcat 의 multipart 파싱과 크기 제한 (MockMvc 는 multipart 를 파싱하지 않아 확인할 수 없다)
 */
@DisplayName("[통합] 첨부파일 업로드 실제 HTTP 테스트")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
class FileUploadHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("fail case : 20MB 를 넘는 파일은 컨트롤러에 오기 전에 4003 으로 거절한다")
    void _20MB_를_넘는_파일은_컨트롤러에_오기_전에_4003_으로_거절한다() throws Exception {
        // given
        String boundary = "----" + UUID.randomUUID();
        byte[] head = ("--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"big.bin\"\r\n"
            + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + 20 * 1024 * 1024 + 1 + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(tail, 0, body, body.length - tail.length, tail.length);

        // when
        HttpResponse<String> response = post(boundary, body);

        // then
        assertThat(response.statusCode()).as(response.body()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":4003");
    }

    @Test
    @DisplayName("success case : 로그 필터를 거쳐도 multipart 파일 부분을 읽을 수 있다 (로그인 안 했으므로 1003 까지 도달)")
    void 로그_필터를_거쳐도_multipart_파일_부분을_읽을_수_있다() throws Exception {
        // given
        String boundary = "----" + UUID.randomUUID();
        byte[] body = ("--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\n"
            + "Content-Type: text/plain\r\n\r\n"
            + "hello"
            + "\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        // when
        HttpResponse<String> response = post(boundary, body);

        // then : 파일 부분을 못 읽었으면 400 (Required part 'file' is not present) 이다
        assertThat(response.statusCode()).as(response.body()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":1003");
    }

    private HttpResponse<String> post(String boundary, byte[] body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/private/api/file?channelId=1"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    }
}
