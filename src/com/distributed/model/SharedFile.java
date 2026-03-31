package com.distributed.model;

import java.io.Serializable;
import java.util.List;

/**
 * Represents a file available in the distributed system.
 * Contains metadata and integrity verification hashes.
 */
public class SharedFile implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The name of the file */
    private String filename;

    /** Total size of the file in bytes */
    private long fileSize;

    /** SHA-256 hash of the entire file for final validation */
    private String fileHash;

    /** List of SHA-256 hashes for each individual piece of the file */
    private List<String> pieceHashes;

    public SharedFile(String filename, long fileSize, String fileHash, List<String> pieceHashes) {
        this.filename = filename;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.pieceHashes = pieceHashes;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public List<String> getPieceHashes() {
        return pieceHashes;
    }

    public void setPieceHashes(List<String> pieceHashes) {
        this.pieceHashes = pieceHashes;
    }

    @Override
    public String toString() {
        return "SharedFile{" +
                "filename='" + filename + '\'' +
                ", fileSize=" + fileSize +
                ", fileHash='" + fileHash + '\'' +
                '}';
    }
}
