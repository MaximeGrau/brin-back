package com.maximegrau.brin.controller;

import com.maximegrau.brin.api.FeedApi;
import com.maximegrau.brin.api.model.CategoryInfo;
import com.maximegrau.brin.api.model.FeedPage;
import com.maximegrau.brin.model.video.Category;
import com.maximegrau.brin.model.video.MixedFeedCursor;
import com.maximegrau.brin.service.CurrentUser;
import com.maximegrau.brin.service.FeedService;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/v1/feed ("Pour toi" personnalisé, ou une seule catégorie) et GET /api/v1/categories.
 * Routes, paramètres et validation (limit entre 1 et 50, 10 par défaut) viennent de l'interface générée {@link FeedApi}.
 */
@RestController
public class FeedController implements FeedApi {

    private final FeedService feedService;
    private final FeedMapper mapper;
    private final CurrentUser currentUser;

    public FeedController(FeedService feedService, FeedMapper mapper, CurrentUser currentUser) {
        this.feedService = feedService;
        this.mapper = mapper;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<FeedPage> getFeed(com.maximegrau.brin.api.model.Category category,
                                            String cursor, Integer limit) {
        var after = cursor == null || cursor.isBlank() ? null : MixedFeedCursor.decode(cursor);
        var slice = category == null
                ? feedService.getPersonalFeed(currentUser.id(), after, limit)
                : feedService.getCategoryFeed(currentUser.id(), mapper.toDomain(category), after, limit);
        return ResponseEntity.ok(mapper.toPage(slice));
    }

    @Override
    public ResponseEntity<List<CategoryInfo>> getCategories() {
        return ResponseEntity.ok(Arrays.stream(Category.values()).map(mapper::toCategoryInfo).toList());
    }
}
