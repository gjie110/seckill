package com.seckill.controller;

import com.seckill.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/file")
@Tag(name = "文件管理", description = "文件上传相关接口")
public class FileController {

    private static final String UPLOAD_DIR = System.getProperty("user.dir") + File.separator + "uploads";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;

    @Operation(
        summary = "上传图片",
        description = "上传演出海报图片。支持 jpg/png/webp 格式，文件大小不超过 5MB。" +
                      "上传后返回图片的访问 URL，前端可直接引用该 URL 展示图片。"
    )
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("请选择要上传的图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("图片大小不能超过 5MB");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        }
        if (!ext.matches("\\.(jpg|jpeg|png|webp|gif)")) {
            return Result.error("仅支持 jpg/jpeg/png/webp/gif 格式");
        }

        try {
            String dateDir = new SimpleDateFormat("yyyyMMdd").format(new Date());
            File dir = new File(UPLOAD_DIR + File.separator + dateDir);
            if (!dir.exists() && !dir.mkdirs()) {
                return Result.error("创建上传目录失败");
            }
            String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
            Path target = Paths.get(dir.getAbsolutePath(), fileName);
            Files.write(target, file.getBytes());

            String url = "/uploads/" + dateDir + "/" + fileName;
            Map<String, String> data = new HashMap<>();
            data.put("url", url);
            data.put("filename", fileName);
            log.info("[文件上传] 成功: {}", url);
            return Result.success("上传成功", data);
        } catch (IOException e) {
            log.error("[文件上传] 失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }
}
