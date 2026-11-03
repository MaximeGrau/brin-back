package com.maximegrau.brin.model.user;

import com.maximegrau.brin.model.video.InvalidCursorException;
import com.maximegrau.brin.model.video.PageCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * Position dans la bibliothèque : dernier couple {@code (savedAt, videoId)} renvoyé.
 * La date garde toute sa précision (microsecondes en base) pour que la pagination par clé ne saute ni ne répète rien.
 */
public record LibraryCursor(Instant savedAt, UUID videoId) implements PageCursor {

    @Override
    public String encode() {
        var raw = savedAt + "|" + videoId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws InvalidCursorException si le curseur n'a pas été produit par {@link #encode()} */
    public static LibraryCursor decode(String cursor) {
        try {
            var raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            var separator = raw.indexOf('|');
            if (separator < 0) {
                throw new InvalidCursorException(cursor);
            }
            return new LibraryCursor(Instant.parse(raw.substring(0, separator)), UUID.fromString(raw.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidCursorException(cursor);
        }
    }
}
