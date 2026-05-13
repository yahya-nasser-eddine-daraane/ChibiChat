package com.lanmessenger.model;

public record GroupRecord(
    String groupId,
    String name,
    String createdBy
) {
    @Override
    public String toString() {
        return name;
    }
}
