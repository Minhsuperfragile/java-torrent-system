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
// Logic from PeerDaemon.java
Registry centralRegistry = LocateRegistry.getRegistry(serverIp, serverPort);
centralServer = (Server) centralRegistry.lookup(serviceName);

String localIp = InetAddress.getLocalHost().getHostAddress();
User user = new User(username, localIp, p2pPort);
centralServer.registerUser(user);
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
    while (true) {
        Socket clientSocket = serverSocket.accept();
        // Spawn a new thread for every incoming connection
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
        System.out.println("Cleanly unregistered from server.");
    } catch (Exception e) {
        // Handle RMI communication error
    }
}));
```

### Dirty Disconnection (Socket Failures)
If a peer crashes or the network drops mid-transfer, the system handles it at the socket level:

1. **Timeouts:** `socket.setSoTimeout(5000)` prevents the downloader from hanging indefinitely if a peer becomes unresponsive.
2. **Retries:** If `downloadPiece` throws an `IOException` (connection reset, timeout, etc.), the `FileDownloader` catches the exception and puts that specific piece back into the `piecesToDownload` queue for another peer to pick up.

```java
// FileDownloader.java: Retry logic
try {
    if (downloadPiece(peer, pieceIndex)) {
        completedPieces.put(pieceIndex, true);
    } else {
        // Generic failure, put back in queue
        piecesToDownload.add(pieceIndex);
    }
} catch (Exception e) {
    // Network error: log and retry piece
    piecesToDownload.add(pieceIndex);
}
```

## 4. Data Integrity
Every file is split into fixed-size pieces (e.g., 1MB). Before writing a piece to disk, the downloader calculates its `SHA-256` hash and compares it against the metadata received from the central server. This ensures that even if a peer sends corrupt data (either maliciously or due to network noise), the system will detect it and retry the download.
