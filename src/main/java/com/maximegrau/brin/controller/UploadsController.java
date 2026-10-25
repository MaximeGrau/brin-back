package com.maximegrau.brin.controller;

import com.maximegrau.brin.api.UploadsApi;
import com.maximegrau.brin.api.model.Upload;
import com.maximegrau.brin.api.model.UploadRules;
import com.maximegrau.brin.service.CurrentUser;
import com.maximegrau.brin.service.upload.UploadProperties;
import com.maximegrau.brin.service.upload.UploadService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** /api/v1/uploads et /api/v1/me/uploads : publication de shorts sur Brin et tableau de bord de l'auteur. */
@RestController
public class UploadsController implements UploadsApi {

    private final UploadService uploads;
    private final UploadMapper mapper;
    private final UploadProperties properties;
    private final CurrentUser currentUser;

    public UploadsController(UploadService uploads, UploadMapper mapper, UploadProperties properties,
                             CurrentUser currentUser) {
        this.uploads = uploads;
        this.mapper = mapper;
        this.properties = properties;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<UploadRules> getUploadRules() {
        return ResponseEntity.ok(mapper.toRules(properties));
    }

    @Override
    public ResponseEntity<Upload> uploadVideo(MultipartFile file, String title, MultipartFile thumbnail,
                                              String description) {
        var upload = uploads.submit(currentUser.id(), file, thumbnail, title, description);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(mapper.toUpload(upload));
    }

    @Override
    public ResponseEntity<List<Upload>> getMyUploads() {
        return ResponseEntity.ok(uploads.list(currentUser.id()).stream().map(mapper::toUpload).toList());
    }

    @Override
    public ResponseEntity<Void> deleteMyUpload(String uploadId) {
        uploads.delete(currentUser.id(), uploadId);
        return ResponseEntity.noContent().build();
    }
}
