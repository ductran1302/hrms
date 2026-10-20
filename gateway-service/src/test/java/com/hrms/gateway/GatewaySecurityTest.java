package com.hrms.gateway;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityTest {

    /**
     * WebTestClient tự động của Boot chạy ở mock mode (bindToApplicationContext):
     * request là MockServerHttpRequest với URI tương đối → không dùng được cho CORS
     * preflight (isSameOrigin yêu cầu URI tuyệt đối). Mock mode vẫn hợp lệ để test
     * semantics security (401/404, mockJwt).
     */
    @Autowired
    private WebTestClient client;

    /** Client thật (bindToServer) — đi qua Netty, URI tuyệt đối, đúng như production. */
    @LocalServerPort
    private int port;

    private WebTestClient serverClient;

    @BeforeEach
    void setUp() {
        serverClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void healthIsPublic() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void apiWithoutTokenIsRejected() {
        client.get().uri("/api/v1/employees")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authenticatedRequestPassesAuthAnd404sOnUnknownRoute() {
        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .get().uri("/api/unknown-route")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void preflightOptionsIsAllowed() {
        serverClient.options().uri("/api/v1/employees")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
    }

    @Test
    void correlationIdIsGeneratedAndEchoedOnEveryResponse() {
        client.get().uri("/api/v1/employees")
                .exchange()
                .expectHeader().exists("X-Correlation-Id");

        client.get().uri("/api/v1/employees")
                .header("X-Correlation-Id", "corr-fixed-123")
                .exchange()
                .expectHeader().valueEquals("X-Correlation-Id", "corr-fixed-123");
    }
}
