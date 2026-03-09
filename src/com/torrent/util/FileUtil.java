package com.torrent.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for file operations including piece reading and hash calculations.
 */
public class FileUtil {
    /** Fixed size for each file piece: 1MB */
    public static final int PIECE_SIZE = 1024 * 1024;

    /**
     * Reads a specific piece of a file using RandomAccessFile for efficient seeking.
     * 
     * @param filePath   Absolute path to the file.
     * @param pieceIndex Zero-based index of the piece to read.
     * @return Byte array containing the piece data, or null if index is out of bounds.
     * @throws IOException If file reading fails.
     */
    public static byte[] readPiece(String filePath, int pieceIndex) throws IOException {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath, "r")) {
            // Calculate start position in the file
            long offset = (long) pieceIndex * PIECE_SIZE;
            if (offset >= raf.length()) {
                return null;
            }
            // Move file pointer to the start of the piece
            raf.seek(offset);
            
            // Calculate how much to read (usually PIECE_SIZE, unless it's the last piece)
            long remaining = raf.length() - offset;
            int length = (int) Math.min(PIECE_SIZE, remaining);
            
            byte[] buffer = new byte[length];
            raf.readFully(buffer);
            return buffer;
        }
    }

    /**
     * Calculates SHA-256 hash for a given byte array.
     * 
     * @param data The byte array to hash.
     * @return Hexadecimal string representation of the hash.
     * @throws NoSuchAlgorithmException If SHA-256 is not supported.
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
     * Splits a file into pieces and calculates SHA-256 hash for each piece.
     * 
     * @param filePath Absolute path to the file.
     * @return List of hex hashes for all pieces.
     * @throws IOException              If file reading fails.
     * @throws NoSuchAlgorithmException If hashing fails.
     */
    public static List<String> calculatePieceHashes(String filePath) throws IOException, NoSuchAlgorithmException {
        List<String> pieceHashes = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] buffer = new byte[PIECE_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] actualData = buffer;
                // For the last piece, only hash the actual bytes read
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
     * Calculates SHA-256 hash for the entire file.
     * 
     * @param filePath Absolute path to the file.
     * @return Hexadecimal string representation of the full file hash.
     * @throws IOException              If file reading fails.
     * @throws NoSuchAlgorithmException If hashing fails.
     */
    public static String calculateFileHash(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] buffer = new byte[8192]; // Standard 8KB buffer for hashing
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
