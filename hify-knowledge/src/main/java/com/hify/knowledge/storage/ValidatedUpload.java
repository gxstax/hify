package com.hify.knowledge.storage;

/** Validated upload metadata safe to persist and use for deterministic storage. */
public record ValidatedUpload(String fileName, String fileType, long fileSize) {
}
