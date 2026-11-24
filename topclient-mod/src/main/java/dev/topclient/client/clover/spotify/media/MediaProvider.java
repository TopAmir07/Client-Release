package dev.topclient.client.clover.spotify.media;

import dev.topclient.client.clover.spotify.config.SpotifyUIConfig;

interface MediaProvider {
    void start();
    void stop();
    void send(MediaCommand command);
    /** Jump to a point in the song (milliseconds from the start). */
    default void seek(long positionMs) {}
    void setSource(SpotifyUIConfig.Source source);
    String name();
}
