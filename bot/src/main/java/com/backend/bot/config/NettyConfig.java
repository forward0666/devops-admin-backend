package com.backend.bot.config;

import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class NettyConfig {

    @Bean
    public ReactiveWebServerFactory reactiveWebServerFactory() {
        NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory();
        factory.addServerCustomizers(server ->
            server.doOnConnection(conn ->
                      conn.addHandlerLast(new ReadTimeoutHandler(1, TimeUnit.SECONDS))
                  )
                  .accessLog(true)
        );
        return factory;
    }
}
