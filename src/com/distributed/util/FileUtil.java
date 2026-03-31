package com.distributed.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides static utility methods for file manipulation and hashing.
 * Core piece of the system's integrity verification and chunking logic.
 */
public class FileUtil {
    /** Fixed size for file pieces (1 MB) to enable parallel downloading and verification */
    public static final int PIECE_SIZE = 1024 * 1024;

    /**
     * Reads a specific piece of a file from disk.
     * @param filePath Path to the source file
     * @param pieceIndex The index of the piece to read
     * @return Byte array containing the piece data, or null if index is out of bounds
     * @throws IOException If file access fails
     */
    public static byte[] readPiece(String filePath, int pieceIndex) throws IOException {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath, "r")) {
            
            long offset = (long) pieceIndex * PIECE_SIZE;
            if (offset >= raf.length()) {
                return null;
            }
            
            raf.seek(offset);
            
            long remaining = raf.length() - offset;
            int length = (int) Math.min(PIECE_SIZE, remaining);
            
            byte[] buffer = new byte[length];
            raf.readFully(buffer);
            return buffer;
        }
    }

    /**
     * Calculates the SHA-256 hash of a byte array and returns it as a hex string.
     * @param data The data to hash
     * @return Hexadecimal representation of the SHA-256 hash
     * @throws NoSuchAlgorithmException If SHA-256 is not supported
     */
    public static String calculateHash(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Iterates through a file and calculates SHA-256 hashes for every 1MB piece.
     * @param filePath Path to the file to process
     * @return List of hex strings representing piece hashes
     * @throws IOException If file access fails
     * @throws NoSuchAlgorithmException If SHA-256 is not supported
     */
    public static List<String> calculatePieceHashes(String filePath) throws IOException, NoSuchAlgorithmException {
        List<String> pieceHashes = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] buffer = new byte[PIECE_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] actualData = buffer;
                
                if (bytesRead < PIECE_SIZE) {
                    actualData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, actualData, 0, bytesRead);
                }
                pieceHashes.add(calculateHash(actualData));
            }
        }
        return pieceHashes;
    }

    /**
     * Calculates the SHA-256 hash of an entire file.
     * @param filePath Path to the file
     * @return Hex string of the file's SHA-256 hash
     * @throws IOException If file access fails
     * @throws NoSuchAlgorithmException If SHA-256 is not supported
     */
    public static String calculateFileHash(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] buffer = new byte[8192]; 
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
