package config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.clickhouse.ClickHouseDataSource;
import ru.yandex.clickhouse.settings.ClickHouseProperties;

import java.sql.SQLException;

@Configuration
@ConditionalOnProperty(prefix = "clickhouse", name = "enabled", havingValue = "true")
public class ClickHouseConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username:default}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Bean
    public ClickHouseDataSource clickHouseDataSource() throws SQLException {
        ClickHouseProperties props = new ClickHouseProperties();
        props.setUser(username);
        props.setPassword(password);
        return new ClickHouseDataSource(url, props);
    }
}
