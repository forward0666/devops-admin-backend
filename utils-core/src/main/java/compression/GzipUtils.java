package compression;

/**
 * GZIP工具类
 * 中文注释：提供GZIP格式验证的实用工具方法
 *
 * 功能说明：
 * 1. 检测字节数组是否为有效的GZIP格式数据
 * 2. 通过检查GZIP文件头魔数来识别GZIP格式
 *
 * 技术原理：
 * - GZIP格式的文件头以固定的魔数开始
 * - 魔数值为：0x1F 0x8B (十进制: 31, -117)
 * - 检查数据的前两个字节是否匹配GZIP魔数
 *
 * 使用场景：
 * - 验证Cloudflare日志数据是否为GZIP压缩格式
 * - 在处理HTTP请求时识别压缩内容编码
 * - 防止处理非GZIP格式数据导致的解压错误
 *
 * 注意事项：
 * - 该方法只检查文件头，不验证完整的GZIP文件完整性
 * - 对于非常大的文件，建议使用流式处理而不是完整加载到内存
 */

public class GzipUtils {
    /**
     * 检查字节数组是否为GZIP格式
     * 中文注释：通过验证GZIP文件头魔数来识别GZIP压缩格式
     *
     * @param data 要检查的字节数组
     * @return true-是GZIP格式, false-不是GZIP格式或数据为空
     *
     * 验证逻辑：
     * 1. 检查数据不为null
     * 2. 检查数据长度至少为2字节
     * 3. 检查第一个字节是否为0x1F (31)
     * 4. 检查第二个字节是否为0x8B (-117)
     *
     * GZIP格式说明：
     * - 魔数: 0x1F 0x8B
     * - 压缩方法: 0x08 (DEFLATE)
     * - 文件头共10字节，但该方法只验证前2字节
     */

    public static boolean isGzipFormat(byte[] data) {
        return data != null && data.length >= 2 && data[0] == (byte) 0x1f && data[1] == (byte) 0x8b;
    }
}

