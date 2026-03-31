# Distributed File Sharing System: Implementation Report

This report details the architecture and implementation of the **Distributed File Sharing System**. The system consists of a central **RMI Server** (Registry) and multiple **Peer Daemons** that act as both clients (downloaders) and servers (uploaders).

## 1. System Architecture

The system follows a hybrid peer-to-peer (P2P) architecture:
- **Central RMI Server:** Acts as a directory service. Peers register their existence and publish the list of files they are sharing. It does *not* store the files themselves.
- **Peer Daemon:** A background process running on each user's machine. It communicates with the central server via RMI and handles direct P2P file transfers using raw TCP sockets.
- **Peer CLI:** A command-line interface that communicates with the local Peer Daemon to trigger actions like `list`, `download`, and `status`.

---

## 2. Network Connection Lifecycle

The networking in this system is split into two layers: **RMI (Remote Method Invocation)** for control/metadata and **TCP Sockets** for high-performance data transfer.

### Phase 1: Initiation (Registration)
When a `PeerDaemon` starts, it must first announce itself to the central authority.

1. **Locate Registry:** The peer uses `LocateRegistry.getRegistry()` to find the central server's RMI registry.
2. **Lookup Service:** It looks up the `DistributedServer` stub.
3. **Register User:** It calls the remote method `registerUser(User user)`, passing its current IP and the port where it will listen for TCP file requests.

```java
// Logic from PeerDaemon.java: Optimized IP detection
String localIp = ConfigLoader.get("CLIENT_PUBLIC_IP", "");
if (localIp.isEmpty()) {
    // Robust iteration through network interfaces to find non-loopback IPv4
    localIp = getPrivateIPv4(); 
}
localUser = new User(username, localIp, port);
centralServer.registerUser(localUser);
```

### Phase 2: Metadata Exchange (Publishing)
Once registered, the peer scans its local `shared/` folder, calculates file hashes, and sends this metadata to the server. This allows the server to tell other peers who has which file.

```java
// PeerDaemon.java: Scans local files and publishes metadata
List<SharedFile> sharedFilesList = scanLocalFiles();
centralServer.publishFiles(username, sharedFilesList);
```

### Phase 3: P2P Connection (File Transfer)
When a user requests a download, the system finds all peers who have the file and establishes direct TCP connections to them.

#### A. The Downloader (Client)
The `FileDownloader` uses an `ExecutorService` to parallelize the download. It connects to peers via `java.net.Socket`.

```java
// FileDownloader.java: Connecting to a peer to fetch a piece
try (Socket socket = new Socket(peer.getIpAddress(), peer.getPort());
     DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
     DataInputStream dis = new DataInputStream(socket.getInputStream())) {
    
    // Send request: [Filename] + [Piece Index]
    dos.writeUTF(filename);
    dos.writeInt(pieceIndex);
    
    // Receive piece data
    int length = dis.readInt();
    byte[] data = new byte[length];
    dis.readFully(data);
    
    // Verify integrity and write to disk
    verifyAndWrite(data, pieceIndex);
}
```

#### B. The Uploader (Server)
Every `PeerDaemon` runs a `FileTransferServer` (a `ServerSocket` listener) in a background thread to serve incoming requests.

```java
// FileTransferServer.java: Handling an incoming request
try (ServerSocket serverSocket = new ServerSocket(port)) {
    while (!Thread.currentThread().isInterrupted()) {
        Socket clientSocket = serverSocket.accept();
        new Thread(new ClientHandler(clientSocket)).start();
    }
}
```

---

## 3. Handling Disconnection

Robustness in a distributed system requires handling both "clean" and "dirty" disconnections.

### Clean Disconnection (Shutdown Hook)
When the user exits the daemon gracefully (e.g., Ctrl+C or a shutdown command), a **Shutdown Hook** is triggered. This hook calls the central server to remove the user from the global registry so other peers don't try to connect to an offline machine.

```java
// PeerDaemon.java: Graceful exit logic
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    try {
        centralServer.unregisterUser(username);
        System.out.println("\nPeer unregistered from central server.");
    } catch (Exception e) {
        System.err.println("Failed to unregister: " + e.getMessage());
    }
}));
```

### Dirty Disconnection and Automatic Resumption
If a peer crashes or the network drops mid-transfer, the system handles it with a multi-layered recovery strategy:

1. **Socket-Level Timeouts:** `socket.setSoTimeout(5000)` prevents the downloader from hanging indefinitely if a peer becomes unresponsive.
2. **Dynamic Peer Refreshing:** If a download fails, the `FileDownloader` uses a `Supplier<List<User>>` to re-query the central server for an updated list of peers possessing the file. This ensures that if a peer has gone offline permanently, it is removed from the local list of sources.
3. **Automatic Task Re-queuing:** The failed piece index is added back to the `piecesToDownload` queue. The asynchronous scheduler then picks a new peer from the refreshed list and attempts the download again.

```java
// FileDownloader.java: Robust recovery logic
} else {
    // Log failure and notify the downloader to refresh its directory
    System.err.println("Failed to download piece " + pieceIndex + " from " + peer.getUsername() + ", refreshing peer list and retrying...");
    
    // Re-check directory from central server
    refreshPeers();
    
    // Put back in queue for retry with a potentially different peer
    synchronized (piecesToDownload) {
        piecesToDownload.add(pieceIndex);
    }
}
```

## 4. Data Integrity
Every file is split into fixed-size pieces (e.g., 1MB). Before writing a piece to disk, the downloader calculates its `SHA-256` hash and compares it against the metadata received from the central server. This ensures that even if a peer sends corrupt data (either maliciously or due to network noise), the system will detect it and retry the download.

---

## 5. Dynamic Adaptation and Load Balancing

The system implements advanced directory features to ensure the peer list remains fresh and downloads are optimized for network conditions.

### A. Heartbeat System (Liveness Detection & Self-Healing)
To detect "dirty" disconnections where a peer crashes without unregistering, the system uses a **Heartbeat Mechanism**:
1. **Peers:** Every `PeerDaemon` runs a background thread that sends a small "liveness" signal to the central server every 15 seconds.
2. **Server:** The `ServerImpl` runs a "Janitor" thread that scans the registry every 30 seconds. If a peer has not sent a heartbeat for more than 90 seconds, it is automatically pruned from the directory.
3. **Self-Healing:** If the `PeerDaemon` receives a `RemoteException` during heartbeat or publishing (indicating the server has lost its registration metadata), the daemon automatically **self-heals** by re-registering its `localUser` object with the central server.

### B. Optimized Load-Aware Selection
To prevent overloading a single popular peer while maximizing parallelism, the system uses a **Hybrid Randomized-Least-Load strategy**:
1. **Load Reporting:** The `FileTransferServer` tracks the number of active incoming connections. Every time a connection starts or ends, it notifies the central server of its current `load`.
2. **Preference strategy (Top 5 Pool):** When a `FileDownloader` fetches a peer list, it sorts them by their current load. However, to ensure downloads happen in parallel across multiple clients (e.g. your Port 8080 and Port 8081 clients), it picks a peer **randomly from the top 5 least-loaded nodes**.
3. **Result:** This ensures we always target the highest-performing nodes while avoiding the "herd effect" where every parallel thread tries to hit the single least-loaded node simultaneously.

```java
// Logic from FileDownloader.java: Multi-source selection
private User getRandomPeer() {
    List<User> peers = sourcePeers; // Sorted by load
    int poolSize = Math.min(peers.size(), 5);
    // Randomly pick from top 5 best candidates to distribute work in parallel
    return peers.get(random.nextInt(poolSize));
}
```
