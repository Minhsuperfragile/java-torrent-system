package com.distributed.client;

import com.distributed.util.FileUtil;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A TCP server that listens for incoming file piece requests from other peers.
 * Each request is handled in a separate thread.
 */
public class FileTransferServer implements Runnable {
    /** The port on which to listen for TCP connections */
    private final int port;
    /** The local directory where shared files are stored */
    private final String sharedFolderPath;

    /**
     * Constructs a new FileTransferServer.
     * 
     * @param port             The port to bind the ServerSocket to.
     * @param sharedFolderPath The path to the folder containing files to share.
     */
    public FileTransferServer(int port, String sharedFolderPath) {
        this.port = port;
        this.sharedFolderPath = sharedFolderPath;
    }

    /**
     * The main server loop. Accepts incoming socket connections and 
     * delegates them to ClientHandler threads.
     */
    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("File Transfer Server started on port " + port);
            while (!Thread.currentThread().isInterrupted()) {
                // Wait for a peer to connect
                Socket clientSocket = serverSocket.accept();
                // Handle each request in a new thread for concurrency
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("File Transfer Server error: " + e.getMessage());
        }
    }

    /**
     * Inner class to handle a single peer's request for a file piece.
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream dis = new DataInputStream(socket.getInputStream());
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
                
                // 1. Read the requested filename and piece index from the peer
                String filename = dis.readUTF();
                int pieceIndex = dis.readInt();
                
                System.out.println("Received request for " + filename + " piece " + pieceIndex + " from " + socket.getInetAddress());

                // 2. Locate the file in the shared folder
                Path filePath = Paths.get(sharedFolderPath, filename);
                File file = filePath.toFile();
                
                // 3. If file exists, read the specific piece and send it back
                if (file.exists() && file.isFile()) {
                    byte[] pieceData = FileUtil.readPiece(file.getAbsolutePath(), pieceIndex);
                    if (pieceData != null) {
                        // Send piece length first so receiver knows how much to read
                        dos.writeInt(pieceData.length);
                        // Send the actual raw bytes
                        dos.write(pieceData);
                        dos.flush();
                        System.out.println("Sent piece " + pieceIndex + " of " + filename);
                    } else {
                        // Send -1 to indicate piece index is out of bounds
                        dos.writeInt(-1);
                        System.out.println("Piece " + pieceIndex + " of " + filename + " not found (out of bounds)");
                    }
                } else {
                    // Send -1 to indicate file does not exist locally
                    dos.writeInt(-1);
                    System.out.println("File " + filename + " not found in shared folder");
                }
            } catch (IOException e) {
                System.err.println("Error handling client request: " + e.getMessage());
            } finally {
                try {
                    // Always close the socket after the transaction is complete
                    socket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }
}
