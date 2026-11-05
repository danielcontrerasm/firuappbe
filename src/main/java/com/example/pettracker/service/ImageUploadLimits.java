package com.example.pettracker.service;

import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;

/** Uses the servlet limits so image validation cannot retain a different hardcoded cap. */
@Component
public class ImageUploadLimits {
    private final DataSize maxFileSize;
    private final DataSize maxRequestSize;

    public ImageUploadLimits(MultipartProperties properties) {
        maxFileSize = properties.getMaxFileSize();
        maxRequestSize = properties.getMaxRequestSize();
    }

    public void validateImageSize(long bytes) {
        if (maxFileSize.toBytes() >= 0 && bytes > maxFileSize.toBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Pet image must be " + describe(maxFileSize) + " or smaller");
        }
    }

    public String exceededMessage() {
        return "Upload is too large. Maximum file size: " + describe(maxFileSize)
                + "; maximum total request size: " + describe(maxRequestSize) + ".";
    }

    private String describe(DataSize size) {
        long bytes = size.toBytes();
        if (bytes < 0) return "unlimited";
        if (bytes > 0 && bytes % (1024 * 1024) == 0) return size.toMegabytes() + " MB";
        if (bytes > 0 && bytes % 1024 == 0) return size.toKilobytes() + " KB";
        return bytes + " bytes";
    }
}
