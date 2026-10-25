package com.maximegrau.brin.model.video;

import java.util.List;

/** @param nextCursor nul s'il n'y a pas de page suivante */
public record FeedSlice(List<FeedEntry> entries, PageCursor nextCursor) {
}
