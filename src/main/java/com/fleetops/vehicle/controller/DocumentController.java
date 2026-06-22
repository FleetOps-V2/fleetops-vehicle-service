package com.fleetops.vehicle.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.s3.bucket}")
    private String bucket;

    public DocumentController(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @PostMapping("/presigned-url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> getPresignedUrl(
            @RequestParam String vehicleNumber,
            @RequestParam String docType,
            @RequestParam String filename) {

        String safeDocType = docType.replaceAll("[^a-zA-Z0-9_-]", "_");
        String safeFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = vehicleNumber + "/" + safeDocType + "/" + Instant.now().toEpochMilli() + "-" + safeFilename;

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(r -> r.bucket(bucket).key(key))
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        return ResponseEntity.ok(Map.of(
                "uploadUrl", presigned.url().toString(),
                "key", key
        ));
    }

    @GetMapping("/vehicle/{vehicleNumber}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> listDocuments(@PathVariable String vehicleNumber) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(vehicleNumber + "/")
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(request);

        List<Map<String, Object>> docs = response.contents().stream()
                .map(obj -> {
                    String key = obj.key();
                    String filename = key.substring(key.lastIndexOf('/') + 1);
                    String displayName = filename.replaceFirst("^\\d{13}-", "");
                    String[] parts = key.split("/");
                    String docTypeLabel = parts.length >= 2 ? parts[1].replace("_", " ") : "Document";
                    return Map.<String, Object>of(
                            "key", key,
                            "filename", displayName,
                            "docType", docTypeLabel,
                            "size", obj.size(),
                            "lastModified", obj.lastModified().toString()
                    );
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(docs);
    }
}
