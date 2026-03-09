package com.torrent.model;

import java.io.Serializable;
import java.util.List;

public class SharedFile implements Serializable {
    private static final long serialVersionUID = 1L;
    private String filename;
    private long fileSize;
    private String fileHash;
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
