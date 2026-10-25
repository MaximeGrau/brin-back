package com.maximegrau.brin.controller;

import com.maximegrau.brin.api.LibraryApi;
import com.maximegrau.brin.api.model.LibraryEntry;
import com.maximegrau.brin.api.model.LibraryEntryStatus;
import com.maximegrau.brin.api.model.LibraryPage;
import com.maximegrau.brin.model.user.LibraryCursor;
import com.maximegrau.brin.service.CurrentUser;
import com.maximegrau.brin.service.LibraryService;
import java.time.ZoneOffset;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/library : shorts enregistrés (le partage depuis Instagram / TikTok viendra plus tard). */
@RestController
public class LibraryController implements LibraryApi {

    private final LibraryService library;
    private final FeedMapper feedMapper;
    private final CurrentUser currentUser;

    public LibraryController(LibraryService library, FeedMapper feedMapper, CurrentUser currentUser) {
        this.library = library;
        this.feedMapper = feedMapper;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<LibraryPage> getLibrary(String cursor, Integer limit) {
        var after = cursor == null || cursor.isBlank() ? null : LibraryCursor.decode(cursor);
        var slice = library.getSaved(currentUser.id(), after, limit);
        var items = slice.entries().stream()
                .map(saved -> new LibraryEntry(saved.entry().video().getId().toString(),
                        LibraryEntry.OriginEnum.SAVED_FROM_FEED, LibraryEntryStatus.ACCEPTED,
                        saved.savedAt().atOffset(ZoneOffset.UTC))
                        .video(feedMapper.toItem(saved.entry())))
                .toList();
        return ResponseEntity.ok(new LibraryPage(items)
                .nextCursor(slice.nextCursor() == null ? null : slice.nextCursor().encode()));
    }
}
