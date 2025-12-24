package com.admin.security.config;

import com.google.code.kaptcha.text.WordRenderer;
import com.google.code.kaptcha.util.Configurable;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.util.Random;

public class LeftAlignedWordRenderer extends Configurable implements WordRenderer {
    
    @Override
    public BufferedImage renderWord(String word, int width, int height) {
        int fontSize = getConfig().getTextProducerFontSize();
        Font[] fonts = getConfig().getTextProducerFonts(fontSize);
        Color color = getConfig().getTextProducerFontColor();
        int charSpace = getConfig().getTextProducerCharSpace();
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        // 设置高质量渲染
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        
        // 清除背景为纯白色，避免黑边
        g2d.setColor(new Color(255, 255, 255));
        g2d.fillRect(0, 0, width, height);
        
        // 设置文字颜色
        g2d.setColor(color);
        
        Random random = new Random();
        FontRenderContext frc = g2d.getFontRenderContext();
        
        // 从左边开始绘制，左边距设为5px
        int x = 5;
        int y = height / 2 + fontSize / 3; // 垂直居中
        
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            
            // 随机选择字体
            Font font = fonts[random.nextInt(fonts.length)];
            g2d.setFont(font);
            
            // 绘制字符
            g2d.drawString(String.valueOf(ch), x, y);
            
            // 计算下一个字符的位置
            GlyphVector gv = font.createGlyphVector(frc, String.valueOf(ch));
            int charWidth = (int) gv.getVisualBounds().getWidth();
            x += charWidth + charSpace;
        }
        
        g2d.dispose();
        return image;
    }
}