package com.lanmessenger.model;

public record ContactRecord(
    String userId,
    String username,
    String displayName,
    String nickname,
    String publicKey,
    String lanIp,      // null if offline
    int    tcpPort,    // 0 if offline
    String lastSeen
) {}
