package com.distributed.client;

import com.distributed.model.SharedFile;
import com.distributed.model.User;
import com.distributed.server.Server;
import com.distributed.util.ConfigLoader;
import com.distributed.util.FileUtil;
import java.io.File;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PeerDaemon runs as a background process.
 * It handles file transfers, registers with the central server,
 * and exposes an RMI interface for the CLI to control it.
 */
public class PeerDaemon extends UnicastRemoteObject implements ClientInterface {
    private final String username;
    private final int port; // P2P File Transfer Port
    private final String sharedFolderPath;
    private Server centralServer;
    private final int rmiPort;

    public PeerDaemon(String username, int port, int rmiPort) throws RemoteException {
        super();
        this.username = username;
        this.port = port;
        this.rmiPort = rmiPort;
        this.sharedFolderPath = "./shared/" + username;
        File sharedFolder = new File(this.sharedFolderPath);
        if (!sharedFolder.exists()) {
            sharedFolder.mkdirs();
        }
    }

    public void start() {
        try {
            // 1. Connect to Central RMI Server
            String serverIp = ConfigLoader.get("CENTRAL_SERVER_IP", "localhost");
            int serverPort = ConfigLoader.getInt("SERVER_PORT", 1999);
            String serviceName = ConfigLoader.get("SERVICE_NAME", "DistributedServer");

            Registry centralRegistry = LocateRegistry.getRegistry(serverIp, serverPort);
            centralServer = (Server) centralRegistry.lookup(serviceName);

            // 2. Identify local IP and register
            String localIp = InetAddress.getLocalHost().getHostAddress();
            User user = new User(username, localIp, port);

            // 3. Start TCP listener for file requests (P2P Server)
            new Thread(new FileTransferServer(port, sharedFolderPath, (currentLoad) -> {
                try {
                    centralServer.updateLoad(username, currentLoad);
                } catch (RemoteException e) {
                    System.err.println("Failed to update load on server: " + e.getMessage());
                }
            })).start();

            // 4. Register with Central Server
            centralServer.registerUser(user);
            System.out.println("Peer Daemon started for user: " + username);
            System.out.println("P2P Port: " + port);

            // 5. Initial scan and publishing of local files
            publishSharedFiles();

            // 6. Expose local RMI interface for CLI
            Registry localRegistry;
            try {
                localRegistry = LocateRegistry.createRegistry(rmiPort);
            } catch (RemoteException e) {
                localRegistry = LocateRegistry.getRegistry(rmiPort);
            }
            localRegistry.rebind("PeerDaemon", this);
            System.out.println("CLI Interface exposed on RMI port: " + rmiPort);

            // 7. Periodic publisher (background thread)
            Thread publisherThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(60000); // Re-scan every 1 minute
                        publishSharedFiles();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            publisherThread.setDaemon(true);
            publisherThread.start();

            // 8. Heartbeat publisher (background thread)
            Thread heartbeatThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(30000); // Heartbeat every 30 seconds
                        centralServer.heartbeat(username);
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        System.err.println("Heartbeat error: " + e.getMessage());
                    }
                }
            });
            heartbeatThread.setDaemon(true);
            heartbeatThread.start();

            // Shutdown Hook: Cleanly unregister from server
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    centralServer.unregisterUser(username);
                    System.out.println("\nPeer unregistered from central server.");
                } catch (Exception e) {
                    System.err.println("Failed to unregister: " + e.getMessage());
                }
            }));

        } catch (Exception e) {
            System.err.println("Fatal error in PeerDaemon: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void publishSharedFiles() {
        try {
            File folder = new File(sharedFolderPath);
            File[] files = folder.listFiles();
            List<SharedFile> sharedFilesList = new ArrayList<>();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String filename = file.getName();
                        long fileSize = file.length();
                        String fileHash = FileUtil.calculateFileHash(file.getAbsolutePath());
                        List<String> pieceHashes = FileUtil.calculatePieceHashes(file.getAbsolutePath());
                        sharedFilesList.add(new SharedFile(filename, fileSize, fileHash, pieceHashes));
                    }
                }
            }

            if (!sharedFilesList.isEmpty()) {
                centralServer.publishFiles(username, sharedFilesList);
                System.out.println("Published " + sharedFilesList.size() + " files to central server.");
            }
        } catch (Exception e) {
            System.err.println("Error publishing files: " + e.getMessage());
        }
    }

    @Override
    public void downloadFile(String filename) throws RemoteException {
        // Run download in a separate thread to not block CLI's RMI call
        new Thread(() -> {
            try {
                SharedFile metadata = centralServer.getFileMetadata(filename);
                if (metadata == null) {
                    System.err.println("File not found on server: " + filename);
                    return;
                }

                List<User> sourcePeers = getAvailablePeers(filename);

                if (sourcePeers.isEmpty()) {
                    System.err.println("No other peers available for: " + filename);
                    return;
                }

                FileDownloader downloader = new FileDownloader(metadata, sourcePeers, sharedFolderPath, () -> {
                    try {
                        return getAvailablePeers(filename);
                    } catch (RemoteException e) {
                        System.err.println("Failed to refresh peer list: " + e.getMessage());
                        return null;
                    }
                });
                if (downloader.download()) {
                    System.out.println("Download successful: " + filename);
                    publishSharedFiles();
                } else {
                    System.err.println("Download failed: " + filename);
                }
            } catch (Exception e) {
                System.err.println("Error during download: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public Map<String, List<String>> getFileLocations() throws RemoteException {
        return centralServer.getFileLocations();
    }

    @Override
    public String getUsername() throws RemoteException {
        return username;
    }

    @Override
    public void shutdown() throws RemoteException {
        System.out.println("Shutdown request received from CLI.");
        System.exit(0);
    }

    private List<User> getAvailablePeers(String filename) throws RemoteException {
        Map<String, List<String>> locations = centralServer.getFileLocations();
        List<String> usernames = locations.get(filename);
        List<User> sourcePeers = new ArrayList<>();

        if (usernames != null) {
            List<User> allUsers = centralServer.getAllUsers();
            for (String uname : usernames) {
                if (uname.equals(this.username)) continue;
                for (User u : allUsers) {
                    if (u.getUsername().equals(uname)) {
                        sourcePeers.add(u);
                        break;
                    }
                }
            }
        }
        return sourcePeers;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java com.distributed.client.PeerDaemon <username> <p2p_port> [rmi_port]");
            System.exit(1);
        }

        String username = args[0];
        int p2pPort = Integer.parseInt(args[1]);
        int rmiPort = (args.length > 2) ? Integer.parseInt(args[2]) : ConfigLoader.getInt("CLIENT_RMI_PORT", 1998);

        try {
            PeerDaemon daemon = new PeerDaemon(username, p2pPort, rmiPort);
            daemon.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
