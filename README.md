# java-torrent-system

This is a prototype of a torrent system, which is a distributed file sharing system. This system is built using Java and is intended to be used as a learning tool for understanding the fundamentals of distributed systems and file sharing. It will be much much simpler than a real torrent system, but it will demonstrate the basic concepts of peer-to-peer file sharing.

### Feature

- This project contain a centralized server that accept RMI request from peers and send back the list of files that are available for download. The server will also handle the file splitting, hashing pieces, and scheduling the download of pieces to peers.
- This project contain a deamon peer that can connect to the server to register itself and can download/upload file to other peers. The peers only connect to the server to get the list of files and the list of peers that have the file. The peers will then connect to each other to download the file using TCP connection. The peers will reassemble the file and do verification of the file.
- Verification is done by comparing the hash of the downloaded file with the hash of the original file. The hash is calculated using SHA256 algorithm. The file is split into pieces of 1MB and each piece is hashed and stored in a separate json file for easy serialization and deserialization. 

### How the project organized

- A centralized server is in `Server.java` and `ServerImpl.java`.
- A deamon peer is in `Peer.java` and `PeerImpl.java`. The deamon peer is a background process that runs in the background and can be started by the user.
- The file splitting and hashing is done in `FileUtil.java`.
- The file reassembly and verification is done in `FileUtil.java`.
