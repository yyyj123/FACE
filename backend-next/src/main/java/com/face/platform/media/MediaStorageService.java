package com.face.platform.media;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class MediaStorageService {

    static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;

    private final Path storageRoot;

    public MediaStorageService(@Value("${face.media.storage-path:./data/media}") String storagePath) {
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public StoredImage storeImage(TenantPrincipal principal, MultipartFile file) {
        requireAdmin(principal);
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图片不能超过 5 MB");
        }

        try {
            byte[] bytes = file.getBytes();
            String extension = detectExtension(bytes);
            String fileName = "t" + principal.tenantId() + "-" + UUID.randomUUID() + "." + extension;
            Files.createDirectories(storageRoot);
            Files.write(
                storageRoot.resolve(fileName),
                bytes,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            );
            return new StoredImage(fileName, "/face-next/upload/" + fileName, bytes.length);
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "图片保存失败，请稍后重试");
        }
    }

    Path storageRoot() {
        return storageRoot;
    }

    private void requireAdmin(TenantPrincipal principal) {
        if (principal == null || principal.roles().stream().noneMatch(
            role -> "ADMIN".equals(role) || "SUPER_ADMIN".equals(role)
        )) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有管理账号可以上传运营图片");
        }
    }

    private String detectExtension(byte[] bytes) {
        if (bytes.length >= 8
            && unsigned(bytes[0]) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E
            && bytes[3] == 0x47 && bytes[4] == 0x0D && bytes[5] == 0x0A
            && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "png";
        }
        if (bytes.length >= 3
            && unsigned(bytes[0]) == 0xFF && unsigned(bytes[1]) == 0xD8
            && unsigned(bytes[2]) == 0xFF) {
            return "jpg";
        }
        if (bytes.length >= 12
            && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
            && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "仅支持 JPG、PNG 或 WebP 图片");
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    public record StoredImage(String fileName, String url, long bytes) {
    }
}
