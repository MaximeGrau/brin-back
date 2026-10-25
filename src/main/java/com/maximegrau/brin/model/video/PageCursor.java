package com.maximegrau.brin.model.video;

/** Position dans le feed, transmise au client sous forme opaque. */
public interface PageCursor {

    String encode();
}
