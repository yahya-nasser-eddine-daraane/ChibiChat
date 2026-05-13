package com.lanmessenger.model;

/** A contact's current online address — returned when they are reachable on the LAN. */
public record PresenceRecord(
    String userId,
    String username,
    String displayName,
    String lanIp,
    int    tcpPort,
    String lastSeen
) {}
