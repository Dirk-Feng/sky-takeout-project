package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

@RestController
@RequestMapping("/admin/common")
@Slf4j
public class CommonController {
    private static final long MAX_SIZE = 2L * 1024 * 1024;
    private final AliOssUtil aliOssUtil;

    public CommonController(AliOssUtil aliOssUtil) { this.aliOssUtil = aliOssUtil; }

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) throw new BaseException("文件不能为空");
        if (file.getSize() >= MAX_SIZE) throw new BaseException("文件大小须小于2MB");
        final byte[] bytes;
        final String extension;
        try {
            bytes = file.getBytes();
            extension = imageExtension(bytes);
        } catch (IOException ex) {
            log.warn("读取上传图片失败: {}", ex.getClass().getSimpleName());
            throw new BaseException(MessageConstant.UPLOAD_FAILED);
        }
        try {
            return Result.success(aliOssUtil.upload(bytes, UUID.randomUUID() + extension));
        } catch (RuntimeException ex) {
            // 不记录 SDK 异常内容，避免日志包含敏感请求信息。
            log.error("OSS上传失败，异常类型: {}", ex.getClass().getSimpleName());
            throw new BaseException(MessageConstant.UPLOAD_FAILED);
        }
    }

    private String imageExtension(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BaseException("仅支持JPEG或PNG图片");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!"JPEG".equalsIgnoreCase(format) && !"PNG".equalsIgnoreCase(format)) throw new BaseException("仅支持JPEG或PNG图片");
                reader.setInput(input, true, true);
                // 限制解码像素数，避免图片解压占用过多内存。
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 25_000_000L || reader.read(0) == null) throw new BaseException("图片内容无效或尺寸过大");
                return "PNG".equalsIgnoreCase(format) ? ".png" : ".jpg";
            } finally { reader.dispose(); }
        }
    }
}
