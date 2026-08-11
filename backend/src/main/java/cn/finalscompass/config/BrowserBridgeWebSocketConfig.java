package cn.finalscompass.config;

import cn.finalscompass.service.AuthService;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/** Registers the browser bridge WebSocket endpoint; the handshake authenticates with the session token. */
@Configuration
@EnableWebSocket
public class BrowserBridgeWebSocketConfig implements WebSocketConfigurer {
    private final BrowserBridgeWebSocketHandler handler;
    private final AuthService auth;

    public BrowserBridgeWebSocketConfig(BrowserBridgeWebSocketHandler handler, AuthService auth) {
        this.handler = handler;
        this.auth = auth;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/browser-bridge").addInterceptors(new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler wsHandler, Map<String, Object> attributes) {
                String token = tokenFrom(request);
                return auth.authenticate(token).map(user -> {
                    attributes.put(BrowserBridgeWebSocketHandler.USER_ID_ATTRIBUTE, user.id());
                    return true;
                }).orElse(false);
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Exception exception) {
            }

            private String tokenFrom(ServerHttpRequest request) {
                if (request instanceof ServletServerHttpRequest servlet) {
                    String token = servlet.getServletRequest().getParameter("token");
                    if (token != null && !token.isBlank()) return token;
                }
                URI uri = request.getURI();
                String query = uri.getQuery();
                if (query == null) return null;
                for (String pair : query.split("&")) {
                    if (pair.startsWith("token=")) return pair.substring(6);
                }
                return null;
            }
        }).setAllowedOriginPatterns("*");
    }
}
