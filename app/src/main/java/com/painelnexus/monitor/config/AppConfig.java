package com.painelnexus.monitor.config;

/** Constantes do monitor de FPS e ping. */
public final class AppConfig {

    public static final String NOTIF_CHANNEL_ID   = "nexus_monitor";
    public static final String NOTIF_CHANNEL_NAME = "Monitor de FPS e Ping";
    public static final int    NOTIF_ID           = 1337;

    /** Destino do teste de ping (DNS publico da Cloudflare, porta HTTPS). */
    public static final String PING_HOST        = "1.1.1.1";
    public static final int    PING_PORT        = 443;
    public static final int    PING_TIMEOUT_MS  = 1500;
    public static final long   PING_INTERVAL_MS = 2000L;

    private AppConfig() {}
}
