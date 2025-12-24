package com.admin.security.config;

import com.google.code.kaptcha.Producer;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class KaptchaConfig {

    @Bean
    public Producer kaptchaProducer() {
        Properties properties = new Properties();
        
        // 无边框，更现代化
        properties.setProperty("kaptcha.border", "no");
        
        // 字体颜色 - 使用黑色，清晰优雅
        properties.setProperty("kaptcha.textproducer.font.color", "0,0,0");
        
        // 字体大小 - 适中大小，清晰可读
        properties.setProperty("kaptcha.textproducer.font.size", "30");
        
        // 字体 - 使用更优雅的字体
        properties.setProperty("kaptcha.textproducer.font.names", "Arial,Helvetica,Microsoft YaHei");
        
        // 验证码长度
        properties.setProperty("kaptcha.textproducer.char.length", "5");
        
        // 字符间距 - 正常间距保持可读性
        properties.setProperty("kaptcha.textproducer.char.space", "6");
        
        // 文字渲染器 - 使用默认文字渲染器
        properties.setProperty("kaptcha.textproducer.impl", "com.google.code.kaptcha.text.impl.DefaultTextCreator");
        
        // 文字绘制器 - 使用默认文字绘制器
        properties.setProperty("kaptcha.word.impl", "com.google.code.kaptcha.text.impl.DefaultWordRenderer");
        
        // 图片宽度 - 适当宽度
        properties.setProperty("kaptcha.image.width", "150");
        
        // 文字位置 - 控制文字在图片中的位置
        properties.setProperty("kaptcha.textproducer.char.x", "10");
        properties.setProperty("kaptcha.textproducer.char.y", "25");
        
        // 图片高度 - 适配输入框
        properties.setProperty("kaptcha.image.height", "36");
        
        // 背景颜色 - 纯白背景，更优雅
        properties.setProperty("kaptcha.background.color.from", "255,255,255");
        properties.setProperty("kaptcha.background.color.to", "255,255,255");
        
        // 干扰线颜色 - 淡化干扰线，更优雅
        properties.setProperty("kaptcha.noise.color", "220,220,220");
        
        // 字符集合 - 去掉容易混淆的字符
        properties.setProperty("kaptcha.textproducer.char.string", "23456789ABCDEFGHJKLMNPQRSTUVWXYZ");
        
        Config config = new Config(properties);
        DefaultKaptcha defaultKaptcha = new DefaultKaptcha();
        defaultKaptcha.setConfig(config);
        
        return defaultKaptcha;
    }
}