package com.smim.server.live;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket. SockJS 는 쓰지 않는다 — 클라이언트가 React Native 라 WebSocket 이 항상 있다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	/** 10초마다 서로 확인한다. Wi-Fi ↔ LTE 전환처럼 소켓이 조용히 죽는 경우를 이것으로 알아챈다 */
	static final long[] HEARTBEAT_MS = { 10_000, 10_000 };

	private final StompAuthInterceptor auth;

	public WebSocketConfig(StompAuthInterceptor auth) {
		this.auth = auth;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// 인증은 Origin 이 아니라 X-Device-Key 로 한다. 쿠키를 쓰지 않으므로 Origin 을 제한해 막을 것이 없다
		registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		ThreadPoolTaskScheduler heartbeat = new ThreadPoolTaskScheduler();
		heartbeat.setPoolSize(1);
		heartbeat.setThreadNamePrefix("ws-heartbeat-");
		heartbeat.initialize();

		registry.enableSimpleBroker("/queue").setHeartbeatValue(HEARTBEAT_MS).setTaskScheduler(heartbeat);
		registry.setUserDestinationPrefix("/user");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(auth);
	}
}
