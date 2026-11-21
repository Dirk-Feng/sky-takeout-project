package com.sky.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import java.io.ByteArrayInputStream;

public class AliOssUtil {
    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String bucketName;

    public AliOssUtil(String endpoint, String accessKeyId, String accessKeySecret, String bucketName) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.bucketName = bucketName;
    }

    public String upload(byte[] bytes, String objectName) {
        OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(objectName.endsWith(".png") ? "image/png" : "image/jpeg");
            client.putObject(bucketName, objectName, new ByteArrayInputStream(bytes), metadata);
        } finally {
            client.shutdown();
        }
        String host = endpoint.replaceFirst("(?i)^https?://", "").replaceAll("/+$", "");
        return "https://" + bucketName + "." + host + "/" + objectName;
    }
}
