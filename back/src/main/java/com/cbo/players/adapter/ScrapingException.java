package com.cbo.players.adapter;

public class ScrapingException extends RuntimeException {
    public enum Kind { CONFIGURATION, CONNECTION, TIMEOUT, INVALID_RESPONSE, REMOTE_HTTP }

    private final Kind kind;
    private final Integer remoteStatus;
    private final String remoteCode;

    public ScrapingException(Kind kind, String message) {
        this(kind, message, null, null);
    }

    public ScrapingException(Kind kind, String message, Integer remoteStatus, String remoteCode) {
        super(message);
        this.kind = kind;
        this.remoteStatus = remoteStatus;
        this.remoteCode = remoteCode;
    }

    public Kind getKind() { return kind; }
    public Integer getRemoteStatus() { return remoteStatus; }
    public String getRemoteCode() { return remoteCode; }
}
