package com.backend.bot.config;

import com.backend.bot.enums.BotType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.MySqlDialect;

import java.util.List;

@Configuration
@EnableR2dbcAuditing
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return R2dbcCustomConversions.of(MySqlDialect.INSTANCE, List.of(
                new BotTypeReadingConverter(),
                new BotTypeWritingConverter()
        ));
    }
}
