package com.tree.twig_tree.global.security.jwt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lettuce 의 커맨드 타임아웃 기본값은 60초다. 그대로 두면 응답하지 않는 Redis 를 장애로
 * 판정하기까지 요청 스레드가 60초를 매달려 있게 되므로, application.yml 에서 줄여 두었다.
 * <p>
 * 이 테스트는 실제 application.yml 을 읽어 들여({@link ConfigDataApplicationContextInitializer})
 * 설정이 사라지면 깨지도록 한다. 연결은 수락하지만 아무 응답도 하지 않는 소켓을 Redis 자리에
 * 놓아, "붙기는 붙었는데 답이 없는" 상태를 재현한다.
 */
class RedisCommandTimeoutTest {

    /** 규정한 타임아웃 상한. 기본값(60초)으로 되돌아가면 이 안에 끝나지 않는다. */
    private static final Duration FAIL_FAST_BUDGET = Duration.ofSeconds(8);

    private ServerSocket blackhole;
    private final List<Socket> accepted = new ArrayList<>();

    @BeforeEach
    void startBlackhole() throws IOException {
        blackhole = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());

        // accept 만 하고 읽지도 쓰지도 않는다. 닫아버리면 즉시 실패라 타임아웃을 검증할 수 없다.
        Thread acceptor = new Thread(() -> {
            while (!blackhole.isClosed()) {
                try {
                    accepted.add(blackhole.accept());
                } catch (IOException e) {
                    return; // 소켓이 닫히면 종료
                }
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @AfterEach
    void stopBlackhole() throws IOException {
        for (Socket socket : accepted) {
            socket.close();
        }
        blackhole.close();
    }

    @Test
    void application_yml에_커맨드_타임아웃이_설정되어_있다() {
        runner().run(context -> {
            DataRedisProperties properties = context.getBean(DataRedisProperties.class);

            assertThat(properties.getTimeout())
                    .as("커맨드 타임아웃 미설정 시 Lettuce 기본값 60초가 적용된다")
                    .isNotNull()
                    .isLessThanOrEqualTo(FAIL_FAST_BUDGET);
            assertThat(properties.getConnectTimeout()).isNotNull();
        });
    }

    @Test
    @Timeout(30) // 기본값으로 회귀하면 60초를 기다리지 않고 여기서 끊는다
    void 응답하지_않는_Redis는_설정한_타임아웃_안에_실패한다() {
        runner().run(context -> {
            StringRedisTemplate redisTemplate = context.getBean(StringRedisTemplate.class);

            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> redisTemplate.hasKey("refresh:probe"))
                    .isInstanceOf(DataAccessException.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

            assertThat(elapsed)
                    .as("60초를 기다리지 않고 끊겨야 한다 (실제 %d ms)", elapsed.toMillis())
                    .isLessThan(FAIL_FAST_BUDGET);
        });
    }

    /**
     * host/port 만 블랙홀로 덮어쓰고 타임아웃 값은 application.yml 에서 그대로 가져온다.
     */
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
                .withPropertyValues(
                        "spring.data.redis.host=" + blackhole.getInetAddress().getHostAddress(),
                        "spring.data.redis.port=" + blackhole.getLocalPort());
    }
}
