package config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class CaffeineConfig {

    @Bean("caffeineCacheManager")
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        log.info("✅ CaffeineCacheManager bean created: {}", cacheManager);

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long maxHeap = memoryMXBean.getHeapMemoryUsage().getMax();
        long maxCacheWeight = maxHeap / 1024 / 10; // 堆内存的 1/10 KB

        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumWeight(maxCacheWeight)
                        .weigher((key, value) -> value.toString().length())
        );

        return cacheManager;
    }
}
