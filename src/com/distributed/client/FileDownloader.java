package com.distributed.client;

import com.distributed.model.SharedFile;
import com.distributed.model.User;
import com.distributed.util.Benchmark;
import com.distributed.util.FileUtil;
import java.io.*;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Handles the logic for downloading a file from multiple peers in parallel.
 * Splits the download into pieces and verifies each piece's integrity.
 */
public class FileDownloader {
    /** Metadata of the file to be downloaded */
    private final SharedFile sharedFile;
    /** List of peers known to have this file */
    private volatile List<User> sourcePeers = new CopyOnWriteArrayList<>();
    /** Supplier to refresh the list of peers from the central server */
    private final Supplier<List<User>> peerProvider;
    /** Directory where the file will be saved */
    private final String downloadPath;
    /** Tracks which pieces have been successfully downloaded and verified */
    private final Map<Integer, Boolean> completedPieces = new ConcurrentHashMap<>();
    /** Thread pool for concurrent piece downloads */
    private final ExecutorService executor;
    /** Concurrent download limit */
    private final int MAX_THREADS = 5;
    /** For random peer selection */
    private static final Random random = new Random();

    public FileDownloader(SharedFile sharedFile, List<User> initialPeers, String downloadPath,
            Supplier<List<User>> peerProvider) {
        this.sharedFile = sharedFile;
        initialPeers.sort(Comparator.comparingInt(User::getLoad));
        this.sourcePeers.addAll(initialPeers);
        this.downloadPath = downloadPath;
        this.peerProvider = peerProvider;
        this.executor = Executors.newFixedThreadPool(MAX_THREADS);
    }

    /**
     * Executes the multi-threaded download process.
     * 
     * @return true if the entire file was downloaded and verified successfully.
     */
    public boolean download() {
        Benchmark benchmark = new Benchmark(sharedFile.getFilename());
        benchmark.start();

        int totalPieces = sharedFile.getPieceHashes().size();
        System.out.println("Starting download of " + sharedFile.getFilename() + " (" + totalPieces + " pieces)");

        File targetFile = new File(downloadPath, sharedFile.getFilename());

        // 1. Pre-allocate the file on disk with the expected total size.
        // This ensures enough space and allows writing pieces at any offset.
        try (RandomAccessFile raf = new RandomAccessFile(targetFile, "rw")) {
            raf.setLength(sharedFile.getFileSize());
        } catch (IOException e) {
            System.err.println("Failed to initialize target file: " + e.getMessage());
            return false;
        }

        // 2. Queue all pieces for download.
        Queue<Integer> piecesToDownload = new LinkedList<>();
        for (int i = 0; i < totalPieces; i++) {
            piecesToDownload.add(i);
        }

        AtomicInteger activeDownloads = new AtomicInteger(0);

        // 3. Main scheduler loop: runs until all pieces are completed.
        while (completedPieces.size() < totalPieces) {
            if (!piecesToDownload.isEmpty() && activeDownloads.get() < MAX_THREADS) {
                int pieceIndex = piecesToDownload.poll();

                // Randomly select a peer for this specific piece to balance network load.
                User peer = getRandomPeer();
                if (peer == null) {
                    System.err.println("No peers available for download");
                    break;
                }

                activeDownloads.incrementAndGet();
                // Submit download task to thread pool
                executor.submit(() -> {
                    try {
                        if (downloadPiece(peer, pieceIndex, targetFile)) {
                            // Mark as complete if hash matches
                            completedPieces.put(pieceIndex, true);
                            System.out.println(
                                    "Piece " + pieceIndex + " downloaded successfully from " + peer.getUsername());
                        } else {
                            // On failure (disconnect/hash mismatch), put back in queue for retry
                            System.err.println("Failed to download piece " + pieceIndex + " from " + peer.getUsername()
                                    + ", refreshing peer list and retrying...");

                            // Re-check directory from central server as requested
                            refreshPeers();

                            synchronized (piecesToDownload) {
                                piecesToDownload.add(pieceIndex);
                            }
                        }
                    } finally {
                        activeDownloads.decrementAndGet();
                    }
                });
            } else {
                // Throttle the loop to prevent high CPU usage while waiting for threads.
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 4. Cleanup executor and wait for any lingering tasks.
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 5. Final validation.
        if (completedPieces.size() == totalPieces) {
            benchmark.stop();
            System.out.println("Download complete: " + sharedFile.getFilename());
            System.out.println(benchmark.getFormattedSummary(sharedFile.getFileSize()));

            // Verify full file hash to ensure no corruption occurred during assembly.
            try {
                String downloadedHash = FileUtil.calculateFileHash(targetFile.getAbsolutePath());
                if (downloadedHash.equals(sharedFile.getFileHash())) {
                    System.out.println("File integrity verified!");
                    return true;
                } else {
                    System.err.println("File integrity check failed!");
                    return false;
                }
            } catch (IOException | NoSuchAlgorithmException e) {
                System.err.println("Error verifying file integrity: " + e.getMessage());
                return false;
            }
        } else {
            System.err.println(
                    "Download failed: only " + completedPieces.size() + "/" + totalPieces + " pieces downloaded.");
            return false;
        }
    }

    /**
     * Picks the best peer (least loaded) from the list of sources.
     */
    /**
     * Picks a peer from the top 5 least loaded sources.
     * We pick randomly among the top candidates to distribute work in parallel.
     */
    private User getRandomPeer() {
        List<User> peers = sourcePeers;
        if (peers.isEmpty())
            return null;

        // Ensure the list is sorted by load (it usually is from refreshPeers, but just
        // in case)
        // Note: For extreme efficiency with many peers, we'd avoid sorting every time,
        // but here we only have a handful.

        // Only consider the top 5 BEST (least loaded) peers
        int poolSize = Math.min(peers.size(), 5);

        // Randomly pick from these top candidates to ensure parallel downloads
        return peers.get(random.nextInt(poolSize));
    }

    /**
     * Refreshes the list of source peers from the central server.
     */
    private void refreshPeers() {
        if (peerProvider != null) {
            List<User> updatedPeers = peerProvider.get();
            if (updatedPeers != null && !updatedPeers.isEmpty()) {
                // Sort by load (least busy first) to optimize source selection
                updatedPeers.sort(Comparator.comparingInt(User::getLoad));
                sourcePeers = new CopyOnWriteArrayList<>(updatedPeers);
                System.out.println("Peer list refreshed. Now " + sourcePeers.size() + " peers available. Best peer: "
                        + sourcePeers.get(0).getUsername() + " (Load: " + sourcePeers.get(0).getLoad() + ")");
            }
        }
    }

    /**
     * Connects to a specific peer via TCP, requests a single piece,
     * verifies its hash, and writes it to the local file.
     * 
     * @param peer       Target peer information.
     * @param pieceIndex Index of the piece to fetch.
     * @param targetFile Local file handle for writing pieces.
     * @return true if download and verification succeeded.
     */
    private boolean downloadPiece(User peer, int pieceIndex, File targetFile) {
        try (Socket socket = new Socket(peer.getIpAddress(), peer.getPort());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            // Set a timeout to prevent hanging if a peer is unresponsive.
            socket.setSoTimeout(5000);

            // Request protocol: [Filename (UTF)] + [Piece Index (Int)]
            dos.writeUTF(sharedFile.getFilename());
            dos.writeInt(pieceIndex);
            dos.flush();

            // Receive piece length
            int length = dis.readInt();
            if (length == -1) {
                return false; // Error reported by peer
            }

            // Read the raw bytes
            byte[] data = new byte[length];
            dis.readFully(data);

            // Piece verification: Compare local hash with expected metadata hash.
            String calculatedHash = FileUtil.calculateHash(data);
            String expectedHash = sharedFile.getPieceHashes().get(pieceIndex);
            if (!calculatedHash.equals(expectedHash)) {
                System.err.println("Piece " + pieceIndex + " hash mismatch!");
                return false;
            }

            // Thread-safe write to the specific offset in the file.
            try (RandomAccessFile raf = new RandomAccessFile(targetFile, "rw")) {
                raf.seek((long) pieceIndex * FileUtil.PIECE_SIZE);
                raf.write(data);
            }

            return true;
        } catch (IOException | NoSuchAlgorithmException e) {
            System.err.println(
                    "Error downloading piece " + pieceIndex + " from " + peer.getUsername() + ": " + e.getMessage());
            return false;
        }
    }
}
