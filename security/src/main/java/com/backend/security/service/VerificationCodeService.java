package com.backend.security.service;

import com.google.code.kaptcha.Producer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class VerificationCodeService {

    // 使用构造器注入替代@Autowired，这是Java 21的推荐实践
    private final Producer kaptchaProducer;

    // 存储验证码的内存缓存 (生产环境建议使用Redis)
    // 使用Java 21的特性，更简洁的初始化
    private final Map<String, String> codeCache = new ConcurrentHashMap<>();
    // 存储验证码的过期时间
    private final Map<String, Long> codeExpireTime = new ConcurrentHashMap<>();
    
    public VerificationCodeService(Producer kaptchaProducer) {
        this.kaptchaProducer = kaptchaProducer;
    }

    /**
     * 生成验证码
     * @return 包含验证码ID和图片Base64的Map
     */
    public Map<String, String> generateVerificationCode() {
        try {
            // 生成验证码文本
            var codeText = kaptchaProducer.createText();
            
            // 生成验证码图片
            var codeImage = kaptchaProducer.createImage(codeText);
            
            // 生成唯一ID
            var codeId = UUID.randomUUID().toString();
            
            // 将验证码存储到缓存中 (10分钟过期)
            codeCache.put(codeId, codeText.toLowerCase());
            codeExpireTime.put(codeId, System.currentTimeMillis() + 10 * 60 * 1000); // 10分钟过期
            
            // 将图片转换为Base64
            var imageBase64 = imageToBase64(codeImage);
            
            // 使用Java 21的Map.of创建不可变映射
            var result = Map.of(
                "codeId", codeId,
                "imageBase64", "data:image/png;base64," + imageBase64
            );

            log.info("Generated verification code with ID: {}", codeId);
            return result;

        } catch (Exception e) {
            log.error("Failed to generate verification code", e);
            throw new RuntimeException("Failed to generate verification code", e);
        }
    }

    /**
     * 验证验证码
     * @param codeId 验证码ID
     * @param inputCode 用户输入的验证码
     * @return 是否验证成功
     */
    public boolean validateVerificationCode(String codeId, String inputCode) {
        // 使用Java 21的模式匹配，简化空值检查
        if (codeId == null || inputCode == null) {
            return false;
        }
        
        // 检查验证码是否存在
        var cachedCode = codeCache.get(codeId);
        if (cachedCode == null) {
            log.warn("Verification code not found for ID: {}", codeId);
            return false;
        }

        // 检查是否过期
        var expireTime = codeExpireTime.get(codeId);
        if (expireTime == null || System.currentTimeMillis() > expireTime) {
            log.warn("Verification code expired for ID: {}", codeId);
            // 清除过期的验证码
            codeCache.remove(codeId);
            codeExpireTime.remove(codeId);
            return false;
        }

        // 验证后立即删除验证码
        var isValid = cachedCode.equals(inputCode.toLowerCase());
        log.info("Verification code validation result for ID {}: {}", codeId, isValid);
        
        // 使用Java 21的模式匹配，优化验证结果处理
        if (isValid) {
            codeCache.remove(codeId);
            codeExpireTime.remove(codeId);
            return true;
        }
        return false; // 验证失败时保留验证码以便重试
    }

    /**
     * 将BufferedImage转换为Base64字符串
     */
    private String imageToBase64(BufferedImage image) throws IOException {
        // 使用Java 21的特性，try-with-resources更简洁
        try (var baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            var imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        }
    }
}