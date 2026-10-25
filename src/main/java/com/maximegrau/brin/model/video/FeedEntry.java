package com.maximegrau.brin.model.video;


/** Une vidéo du feed vue par un utilisateur donné. */
public record FeedEntry(Video video, boolean learned, boolean saved) {
}
