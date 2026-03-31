package com.distributed.client;

import com.distributed.model.SharedFile;
import com.distributed.model.User;
import com.distributed.server.Server;
import com.distributed.util.ConfigLoader;
import com.distributed.util.FileUtil;
import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * The core background process of every node in the distributed system.
 * It serves three main purposes:
 * 1. Uploader: Runs a FileTransferServer to serve parts of files to other
 * peers.
 * 2. Downloader: Implements downloadFile to fetch pieces from multiple sources.
 * 3. Client Interface: Exposes an RMI interface (ClientInterface) for local
 * PeerCLI control.
 */
public class PeerDaemon extends UnicastRemoteObject implements ClientInterface {
    /** Unique username assigned to this peer */
    private final String username;
    /** Port number for the TCP FileTransferServer */
    private final int port;
    /** Absolute path to the directory being shared by this peer */
    private final String sharedFolderPath;
    /** Remote reference to the central RMI directory server */
    private Server centralServer;
    /** Port for the local RMI registry (used by CLI) */
    private final int rmiPort;
    /** Metadata object representing this peer's identity and status */
    private User localUser;

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

    /**
     * Initializes the daemon's networking stack, registers with the central server,
     * starts background uploader/heartbeat threads, and exposes the CLI interface.
     */
    public void start() {
        try {
            // 1. Connect to Central RMI Server
            String serverIp = ConfigLoader.get("CENTRAL_SERVER_IP", "localhost");
            int serverPort = ConfigLoader.getInt("SERVER_PORT", 1999);
            String serviceName = ConfigLoader.get("SERVICE_NAME", "DistributedServer");

            Registry centralRegistry = LocateRegistry.getRegistry(serverIp, serverPort);
            centralServer = (Server) centralRegistry.lookup(serviceName);

            // 2. Identify local network address
            String localIp = ConfigLoader.get("CLIENT_PUBLIC_IP", "");
            if (localIp.isEmpty()) {
                localIp = getPrivateIPv4(); // Detect LAN IP if not specified
            }
            this.localUser = new User(username, localIp, port);

            // 3. Start TCP P2P server (uploader background task)
            new Thread(new FileTransferServer(port, sharedFolderPath, (currentLoad) -> {
                try {
                    // Update server on how many uploads we are handling
                    centralServer.updateLoad(username, currentLoad);
                } catch (RemoteException e) {
                    System.err.println("Failed to update load on server: " + e.getMessage());
                }
            })).start();

            // 4. Initial registry with central server
            centralServer.registerUser(localUser);
            System.out.println("Peer Daemon started for user: " + username);
            System.out.println("Registration IP: " + localIp);
            System.out.println("P2P Port: " + port);

            // 5. Scan local "shared/" folder and publish metadata
            publishSharedFiles(localUser);

            // 6. Expose local RMI service so PeerCLI can control this daemon
            Registry localRegistry;
            try {
                localRegistry = LocateRegistry.createRegistry(rmiPort);
            } catch (RemoteException e) {
                localRegistry = LocateRegistry.getRegistry(rmiPort);
            }
            localRegistry.rebind("PeerDaemon", this);
            System.out.println("CLI Interface exposed on RMI port: " + rmiPort);

            // 7. Background: Periodically re-scan folder and publish updates
            Thread publisherThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(60000);
                        publishSharedFiles(localUser);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            publisherThread.setDaemon(true);
            publisherThread.start();

            // 8. Background: Heartbeat thread (Liveness Detection & Self-Healing)
            Thread heartbeatThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(15000); // 15s heartbeats
                        centralServer.heartbeat(username);
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        System.err.println("Heartbeat error (connecting to " + serverIp + ":" + serverPort + "): "
                                + e.getMessage());

                        // SELF-HEALING: If server lost our session, re-connect and re-register
                        try {
                            Registry r = LocateRegistry.getRegistry(serverIp, serverPort);
                            centralServer = (Server) r.lookup(serviceName);
                            centralServer.registerUser(localUser);
                            System.out.println("Heartbeat: Reconnected and re-registered with central server.");
                        } catch (Exception ex) {
                            // Silently retry on next tick
                        }
                    }
                }
            });
            heartbeatThread.setDaemon(true);
            heartbeatThread.start();

            // 9. Shutdown Hook for clean unregistration
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

    /**
     * Scans the local shared folder, generates hashes for all files and their
     * pieces,
     * and sends this metadata to the central server.
     */
    private void publishSharedFiles(User user) {
        try {
            File folder = new File(sharedFolderPath);
            File[] files = folder.listFiles();
            List<SharedFile> sharedFilesList = new ArrayList<>();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String filename = file.getName();
                        long fileSize = file.length();
                        // Intensive crypto: hash entire file for final validation
                        String fileHash = FileUtil.calculateFileHash(file.getAbsolutePath());
                        // Piecewise hashing: allows chunk verification during download
                        List<String> pieceHashes = FileUtil.calculatePieceHashes(file.getAbsolutePath());
                        sharedFilesList.add(new SharedFile(filename, fileSize, fileHash, pieceHashes));
                    }
                }
            }

            if (!sharedFilesList.isEmpty()) {
                try {
                    centralServer.publishFiles(username, sharedFilesList);
                    System.out.println("Published " + sharedFilesList.size() + " files to central server.");
                } catch (RemoteException re) {
                    // Logic to handle server restarts (where server state is lost)
                    if (re.getMessage().contains("not registered")) {
                        System.out.println("Publish: Server forgot us. Re-registering...");
                        centralServer.registerUser(user);
                        centralServer.publishFiles(username, sharedFilesList);
                        System.out.println("Publish: Re-registration and publish successful.");
                    } else {
                        throw re;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error publishing files: " + e.getMessage());
        }
    }

    /**
     * Remote method called via RMI to initiate a download.
     * Spawns a background thread to handle the multi-source transfer.
     */
    @Override
    public void downloadFile(String filename) throws RemoteException {

        new Thread(() -> {
            try {
                // Fetch full metadata including piece hashes
                SharedFile metadata = centralServer.getFileMetadata(filename);
                if (metadata == null) {
                    System.err.println("File not found on server: " + filename);
                    return;
                }

                // Identify which peers currently possess this file
                List<User> sourcePeers = getAvailablePeers(filename);

                if (sourcePeers.isEmpty()) {
                    System.err.println("No other peers available for: " + filename);
                    return;
                }

                // Delegate to the parallel FileDownloader component
                FileDownloader downloader = new FileDownloader(metadata, sourcePeers, sharedFolderPath, () -> {
                    try {
                        // Refresh logic: if some peers go offline, we ask the server for new ones
                        return getAvailablePeers(filename);
                    } catch (RemoteException e) {
                        System.err.println("Failed to refresh peer list: " + e.getMessage());
                        return null;
                    }
                });

                if (downloader.download()) {
                    System.out.println("Download successful: " + filename);
                    // Share the newly downloaded file with others
                    publishSharedFiles(localUser);
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

    /**
     * Cross-references the list of file holders with the list of online users
     * to find active download sources, excluding the local user.
     */
    private List<User> getAvailablePeers(String filename) throws RemoteException {
        Map<String, List<String>> locations = centralServer.getFileLocations();
        List<String> usernames = locations.get(filename);
        List<User> sourcePeers = new ArrayList<>();

        if (usernames != null) {
            List<User> allUsers = centralServer.getAllUsers();
            for (String uname : usernames) {
                // Don't download from ourselves
                if (uname.equals(this.username))
                    continue;
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

    /**
     * Attempts to find a non-loopback IPv4 address for this machine.
     * Essential for P2P connections on a local network.
     */
    private String getPrivateIPv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // Filter out virtual, loopback, or disconnected interfaces
                if (iface.isLoopback() || !iface.isUp())
                    continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // We only care about standard IPv4 addresses
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            // Fallback to basic method
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
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
