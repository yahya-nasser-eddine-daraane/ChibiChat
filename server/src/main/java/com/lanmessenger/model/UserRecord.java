package com.lanmessenger.model;

public record UserRecord(
    String userId,
    String username,        // unique @handle
    String displayName,     // human-readable name
    String passwordHash,
    String passwordSalt,
    String publicKey
) {}
