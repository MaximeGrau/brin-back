package com.maximegrau.brin.service.auth;

import com.maximegrau.brin.model.user.AuthProvider;
import com.maximegrau.brin.model.user.User;
import com.maximegrau.brin.model.user.UserIdentity;
import com.maximegrau.brin.model.video.Category;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Vue du compte construite dans la transaction (les collections de {@link User} sont paresseuses). */
public record UserProfile(
        UUID id,
        String email,
        boolean emailVerified,
        String displayName,
        Set<AuthProvider> providers,
        boolean hasPassword,
        Set<Category> preferredCategories,
        boolean categoriesChosen) {

    public static UserProfile of(User user) {
        return new UserProfile(
                user.getId(),
                user.getEmail(),
                user.isEmailVerified(),
                user.getDisplayName(),
                user.getIdentities().stream().map(UserIdentity::getProvider)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(AuthProvider.class))),
                user.hasPassword(),
                user.getPreferredCategories().isEmpty()
                        ? EnumSet.noneOf(Category.class) : EnumSet.copyOf(user.getPreferredCategories()),
                user.hasChosenCategories());
    }
}
