package com.maximegrau.brin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maximegrau.brin.config.SecurityConfig;
import com.maximegrau.brin.model.user.AuthProvider;
import com.maximegrau.brin.model.video.Category;
import com.maximegrau.brin.model.video.FeedSlice;
import com.maximegrau.brin.service.CurrentUser;
import com.maximegrau.brin.service.FeedService;
import com.maximegrau.brin.service.LibraryService;
import com.maximegrau.brin.service.auth.AccountService;
import com.maximegrau.brin.service.auth.AuthProperties;
import com.maximegrau.brin.service.auth.AuthService;
import com.maximegrau.brin.service.auth.OAuthLoginService;
import com.maximegrau.brin.service.auth.EmailVerificationService;
import com.maximegrau.brin.service.auth.JwtTokens;
import com.maximegrau.brin.service.auth.UserProfile;
import com.maximegrau.brin.service.upload.UploadProperties;
import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Règles d'accès de SecurityConfig, de bout en bout dans la couche web (services simulés). */
@WebMvcTest(controllers = {FeedController.class, AuthController.class, AccountController.class},
        properties = "spring.docker.compose.enabled=false")
@Import({SecurityConfig.class, JwtTokens.class, CurrentUser.class, AccountMapper.class, FeedMapper.class, MediaUrls.class})
@EnableConfigurationProperties({UploadProperties.class, AuthProperties.class})
class SecurityWebTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtTokens jwt;

    @MockitoBean
    private FeedService feedService;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private OAuthLoginService oauthLoginService;
    @MockitoBean
    private AccountService accountService;
    @MockitoBean
    private EmailVerificationService verificationService;
    @MockitoBean
    private LibraryService libraryService;

    @Test
    void leFeedExigeUnJeton() throws Exception {
        mvc.perform(get("/api/v1/feed"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/v1/feed").header("Authorization", "Bearer pas-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void leFeedEstServiAvecUnJetonValide() throws Exception {
        var userId = UUID.randomUUID();
        when(feedService.getPersonalFeed(eq(userId), any(), eq(10))).thenReturn(new FeedSlice(List.of(), null));

        mvc.perform(get("/api/v1/feed").header("Authorization", "Bearer " + jwt.accessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void unStateOAuthNEstPasUnJetonDAcces() throws Exception {
        mvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + jwt.oauthState(AuthProvider.GOOGLE, "brin://auth")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void leProfilEstCeluiDuJeton() throws Exception {
        var userId = UUID.randomUUID();
        when(accountService.profile(userId)).thenReturn(new UserProfile(userId, "lea@example.com", true, "Léa",
                EnumSet.of(AuthProvider.GOOGLE), false, Set.of(Category.HISTOIRE), true));

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + jwt.accessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.providers[0]").value("google"))
                .andExpect(jsonPath("$.preferredCategories[0]").value("HISTOIRE"));
    }

    @Test
    void lesCategoriesEtLAuthentificationSontPubliques() throws Exception {
        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
        when(oauthLoginService.authorizationRedirect(AuthProvider.TIKTOK, "brin://auth"))
                .thenReturn(URI.create("https://www.tiktok.com/v2/auth/authorize/?client_key=k"));

        mvc.perform(get("/api/v1/auth/oauth/tiktok/authorize").param("redirectUri", "brin://auth"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://www.tiktok.com/v2/auth/authorize/?client_key=k"));
    }

    @Test
    void linscriptionValideLeCorpsSelonLeContrat() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "pas-une-adresse", "password": "court"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
