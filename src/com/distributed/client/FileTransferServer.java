package com.distributed.client;

import com.distributed.util.FileUtil;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Dedicated TCP server for handling outgoing file piece requests.
 * Runs in the background of every PeerDaemon to allow other peers to download.
 */
public class FileTransferServer implements Runnable {

    /** Port number to listen for incoming TCP connections */
    private final int port;

    /** Path to the folder containing locally shared files */
    private final String sharedFolderPath;

    /** Current number of active client connections */
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    /** Callback to notify the PeerDaemon of load changes */
    private final Consumer<Integer> loadListener;

    public FileTransferServer(int port, String sharedFolderPath, Consumer<Integer> loadListener) {
        this.port = port;
        this.sharedFolderPath = sharedFolderPath;
        this.loadListener = loadListener;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("File Transfer Server started on port " + port);
            while (!Thread.currentThread().isInterrupted()) {
                // Wait for an incoming connection from a peer wanting to download
                Socket clientSocket = serverSocket.accept();

                // Spawn a handler thread so multiple peers can download simultaneously
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("File Transfer Server error: " + e.getMessage());
        }
    }

    /**
     * Handles a single peer's request for a file piece.
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            // Increment load and notify the daemon (which notifies the central server)
            int currentLoad = activeConnections.incrementAndGet();
            if (loadListener != null)
                loadListener.accept(currentLoad);

            try (DataInputStream dis = new DataInputStream(socket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                // Read request: [FileName] + [PieceIndex]
                String filename = dis.readUTF();
                int pieceIndex = dis.readInt();

                System.out.println("Received request for " + filename + " piece " + pieceIndex + " from "
                        + socket.getInetAddress());

                // Find the file in our local shared folder
                Path filePath = Paths.get(sharedFolderPath, filename);
                File file = filePath.toFile();

                if (file.exists() && file.isFile()) {
                    // Read the specific chunk from disk
                    byte[] pieceData = FileUtil.readPiece(file.getAbsolutePath(), pieceIndex);
                    if (pieceData != null) {
                        // Send [Length] + [Bytes]
                        dos.writeInt(pieceData.length);
                        dos.write(pieceData);
                        dos.flush();
                        System.out.println("Sent piece " + pieceIndex + " of " + filename);
                    } else {
                        // Notify peer that piece index is invalid
                        dos.writeInt(-1);
                        System.out.println("Piece " + pieceIndex + " of " + filename + " not found (out of bounds)");
                    }
                } else {
                    // Notify peer that the file is missing
                    dos.writeInt(-1);
                    System.out.println("File " + filename + " not found in shared folder");
                }
            } catch (IOException e) {
                System.err.println("Error handling client request: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore close errors
                } finally {
                    // Decrement load when transfer is finished
                    int finalLoad = activeConnections.decrementAndGet();
                    if (loadListener != null)
                        loadListener.accept(finalLoad);
                }
            }
        }
    }
}
