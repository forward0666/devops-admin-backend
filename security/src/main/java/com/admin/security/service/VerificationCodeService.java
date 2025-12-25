package com.admin.security.service;

import com.google.code.kaptcha.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VerificationCodeService {

    private static final Logger logger = LoggerFactory.getLogger(VerificationCodeService.class);

    // 使用构造器注入替代@Autowired，这是Java 21的推荐实践
    private final Producer kaptchaProducer;

    // 存储验证码的内存缓存 (生产环境建议使用Redis)
    // 使用Java 21的特性，更简洁的初始化
    private final Map<String, String> codeCache = new ConcurrentHashMap<>();
    
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
            
            // 将验证码存储到缓存中 (5分钟过期)
            codeCache.put(codeId, codeText.toLowerCase());
            
            // 使用Java 21的特性，简化线程创建
            Thread.startVirtualThread(() -> {
                try {
                    Thread.sleep(5 * 60 * 1000); // 5分钟
                    codeCache.remove(codeId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            
            // 将图片转换为Base64
            var imageBase64 = imageToBase64(codeImage);
            
            // 使用Java 21的Map.of创建不可变映射
            var result = Map.of(
                "codeId", codeId,
                "imageBase64", "data:image/png;base64," + imageBase64
            );
            
            logger.info("Generated verification code with ID: {}", codeId);
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to generate verification code", e);
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
        // 使用Java 21的特性，简化空值检查
        if (codeId == null || inputCode == null) {
            return false;
        }
        
        var cachedCode = codeCache.get(codeId);
        if (cachedCode == null) {
            logger.warn("Verification code not found or expired for ID: {}", codeId);
            return false;
        }
        
        // 验证后立即删除验证码
        codeCache.remove(codeId);
        
        // 使用Java 21的特性，简化字符串比较
        var isValid = cachedCode.equals(inputCode.toLowerCase());
        logger.info("Verification code validation result for ID {}: {}", codeId, isValid);
        
        return isValid;
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