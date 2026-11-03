package com.maximegrau.brin.controller;

import com.maximegrau.brin.api.model.AuthTokens;
import com.maximegrau.brin.api.model.User;
import com.maximegrau.brin.model.user.AuthProvider;
import com.maximegrau.brin.model.video.Category;
import com.maximegrau.brin.service.auth.AuthSession;
import com.maximegrau.brin.service.auth.UserProfile;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Conversion domaine ↔ DTO générés pour l'authentification et le compte. */
@Component
public class AccountMapper {

    public AuthTokens toTokens(AuthSession session) {
        return new AuthTokens(session.accessToken(), session.refreshToken(),
                Math.toIntExact(session.expiresInSeconds()), toUser(session.user()));
    }

    public User toUser(UserProfile profile) {
        return new User(
                profile.id().toString(),
                profile.emailVerified(),
                profile.providers().stream()
                        .map(p -> com.maximegrau.brin.api.model.AuthProvider.fromValue(p.name().toLowerCase(Locale.ROOT)))
                        .toList(),
                profile.hasPassword(),
                profile.preferredCategories().stream()
                        .map(c -> com.maximegrau.brin.api.model.Category.valueOf(c.name()))
                        .toList(),
                profile.categoriesChosen())
                .email(profile.email())
                .displayName(profile.displayName());
    }

    public AuthProvider toDomain(com.maximegrau.brin.api.model.AuthProvider provider) {
        return AuthProvider.valueOf(provider.name());
    }

    public Set<Category> toDomain(Collection<com.maximegrau.brin.api.model.Category> categories) {
        return categories.stream()
                .map(c -> Category.valueOf(c.name()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Category.class)));
    }
}
