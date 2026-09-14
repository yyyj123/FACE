package com.face.platform.media;

import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v3/media")
public class MediaController {

    private final MediaStorageService storageService;

    public MediaController(MediaStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public V3ApiResponse<Map<String, Object>> uploadImage(
        @RequestPart("file") MultipartFile file,
        HttpServletRequest request
    ) {
        MediaStorageService.StoredImage stored = storageService.storeImage(
            V3RequestSupport.principal(request), file
        );
        return V3ApiResponse.success(
            Map.of(
                "fileName", stored.fileName(),
                "url", stored.url(),
                "bytes", stored.bytes()
            ),
            V3RequestSupport.requestId(request)
        );
    }
}
