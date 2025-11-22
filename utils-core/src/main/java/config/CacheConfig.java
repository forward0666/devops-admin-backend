package config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class CacheConfig {

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        log.info("✅ CaffeineCacheManager bean created: {}", cacheManager);
        // 获取堆内存信息
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long maxHeap = memoryMXBean.getHeapMemoryUsage().getMax(); // 最大堆内存 bytes

        // 动态计算最大缓存条数或权重
        long maxCacheWeight = maxHeap / 1024 / 10; // 举例：堆内存的 1/10，单位 KB

        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES) // 写入 5 分钟后过期
                        .maximumWeight(maxCacheWeight)         // 最大权重
                        .weigher((key, value) -> {
                            // 估算每条缓存占用的权重，简单按 value.toString().length 计算
                            return value.toString().length();
                        })
        );

        return cacheManager;
    }
}
