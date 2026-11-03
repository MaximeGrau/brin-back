package com.maximegrau.brin.controller;

import com.maximegrau.brin.api.InteractionsApi;
import com.maximegrau.brin.service.CurrentUser;
import com.maximegrau.brin.service.InteractionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/videos/{videoId}/learned|saved|loops|skips : interactions de l'utilisateur, qui nourrissent son feed. */
@RestController
public class InteractionsController implements InteractionsApi {

    private final InteractionService interactions;
    private final CurrentUser currentUser;

    public InteractionsController(InteractionService interactions, CurrentUser currentUser) {
        this.interactions = interactions;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<Void> markLearned(String videoId) {
        interactions.markLearned(currentUser.id(), videoId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unmarkLearned(String videoId) {
        interactions.unmarkLearned(currentUser.id(), videoId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> saveVideo(String videoId) {
        interactions.save(currentUser.id(), videoId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unsaveVideo(String videoId) {
        interactions.unsave(currentUser.id(), videoId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> recordSkip(String videoId) {
        interactions.recordSkip(currentUser.id(), videoId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> recordLoop(String videoId) {
        interactions.recordLoop(currentUser.id(), videoId);
        return ResponseEntity.noContent().build();
    }
}
