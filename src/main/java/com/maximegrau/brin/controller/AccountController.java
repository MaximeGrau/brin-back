package com.maximegrau.brin.controller;

import com.maximegrau.brin.api.AccountApi;
import com.maximegrau.brin.api.model.EmailUpdateRequest;
import com.maximegrau.brin.api.model.EmailVerificationRequest;
import com.maximegrau.brin.api.model.PreferredCategoriesRequest;
import com.maximegrau.brin.api.model.User;
import com.maximegrau.brin.api.model.UserStats;
import com.maximegrau.brin.service.CurrentUser;
import com.maximegrau.brin.service.LibraryService;
import com.maximegrau.brin.service.auth.AccountService;
import com.maximegrau.brin.service.auth.EmailVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/me/** : profil, compteurs, adresse e-mail (et sa validation par code), catégories préférées. */
@RestController
public class AccountController implements AccountApi {

    private final AccountService accountService;
    private final EmailVerificationService verificationService;
    private final LibraryService libraryService;
    private final AccountMapper mapper;
    private final CurrentUser currentUser;

    public AccountController(AccountService accountService, EmailVerificationService verificationService,
                             LibraryService libraryService, AccountMapper mapper, CurrentUser currentUser) {
        this.accountService = accountService;
        this.verificationService = verificationService;
        this.libraryService = libraryService;
        this.mapper = mapper;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<UserStats> getMyStats() {
        var stats = libraryService.stats(currentUser.id());
        return ResponseEntity.ok(new UserStats(Math.toIntExact(stats.learned()), Math.toIntExact(stats.saved()),
                Math.toIntExact(stats.published())));
    }

    @Override
    public ResponseEntity<User> getMe() {
        return ResponseEntity.ok(mapper.toUser(accountService.profile(currentUser.id())));
    }

    @Override
    public ResponseEntity<User> updateEmail(EmailUpdateRequest request) {
        return ResponseEntity.ok(mapper.toUser(accountService.updateEmail(currentUser.id(), request.getEmail())));
    }

    @Override
    public ResponseEntity<Void> sendEmailVerification() {
        verificationService.resend(currentUser.id());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<User> confirmEmailVerification(EmailVerificationRequest request) {
        return ResponseEntity.ok(mapper.toUser(verificationService.confirm(currentUser.id(), request.getCode())));
    }

    @Override
    public ResponseEntity<User> updatePreferredCategories(PreferredCategoriesRequest request) {
        var categories = mapper.toDomain(request.getCategories());
        return ResponseEntity.ok(mapper.toUser(accountService.updatePreferredCategories(currentUser.id(), categories)));
    }
}
