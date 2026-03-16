# java-distributed-system

This is a prototype of a distributed system, which is a distributed file sharing system. This system is built using Java and is intended to be used as a learning tool for understanding the fundamentals of distributed systems and file sharing. It will be much much simpler than a real distributed system, but it will demonstrate the basic concepts of peer-to-peer file sharing.

### Feature

- This project contain a centralized server that accept RMI request from peers and send back the list of files that are available for download. The server will also handle the file splitting, hashing pieces, and scheduling the download of pieces to peers.
- This project contain a deamon peer that can connect to the server to register itself and can download/upload file to other peers. The peers only connect to the server to get the list of files and the list of peers that have the file. The peers will then connect to each other to download the file using TCP connection. The peers will reassemble the file and do verification of the file.
- Verification is done by comparing the hash of the downloaded file with the hash of the original file. The hash is calculated using SHA256 algorithm. The file is split into pieces of 1MB and each piece is hashed and stored in a separate json file for easy serialization and deserialization. 

### How the project organized

- A centralized server is in `Server.java` and `ServerImpl.java`.
- A deamon peer is in `Peer.java` and `PeerImpl.java`. The deamon peer is a background process that runs in the background and can be started by the user. It will register itself to the server and can download/upload file to other peers.
- A client app that have commands "list" and "download". 
- The file splitting and hashing is done in `FileUtil.java`.
- The file reassembly and verification is done in `FileUtil.java`.

## How to Run

### 1. Prerequisites
- Java JDK 8 or higher.
- Compiled classes should be in the `bin` directory or similar.

### 2. Configuration
Copy `.env.example` to `.env` and update the values:
```properties
CENTRAL_SERVER_IP=192.168.100.120 # IP of the machine running the RMIServer
SERVER_PORT=1999                 # Default port is 1999
SERVICE_NAME=DistributedServer
SERVER_PUBLIC_IP=192.168.100.120 # Your machine's LAN IP
```

### 3. Compilation
From the root directory:
```bash
javac -d bin -sourcepath src src/com/distributed/server/RMIServer.java src/com/distributed/client/PeerDaemon.java src/com/distributed/client/PeerCLI.java
```

### 4. Start the Centralized Server
Run the RMI server first:
```bash
java -cp bin com.distributed.server.RMIServer
```

### 5. Start a Peer (Daemon)
Open a new terminal and start a peer daemon. You need to provide a **username** and a **local P2P port**:
```bash
java -cp bin com.distributed.client.PeerDaemon Alice 8080
```
- The daemon will also expose an RMI interface on default port 1998 for the CLI to connect.
- Files you want to share should be placed in `./shared/Alice/`.

### 6. Control the Peer (CLI)
Open another terminal to use the CLI:
```bash
# List all files available in the network
java -cp bin com.distributed.client.PeerCLI list

# Download a file
java -cp bin com.distributed.client.PeerCLI download movie.mp4

# Get daemon info
java -cp bin com.distributed.client.PeerCLI info

# Stop the daemon
java -cp bin com.distributed.client.PeerCLI stop
```

## Network Exposure
To allow other machines on the same network to connect:
1. Ensure `CENTRAL_SERVER_IP` in the peer's `.env` points to the server's LAN IP.
2. Ensure `SERVER_PUBLIC_IP` in the server/peer's `.env` is set to that machine's own LAN IP (found via `ipconfig`).
3. Allow the `SERVER_PORT` (default 1999) through your firewall.
