package com.distributed.model;

import java.io.Serializable;

/**
 * Represents a peer (user) in the distributed network.
 * Stores connection information and current system status.
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Unique identifier for the user */
    private String username;

    /** Network address where the peer's FileTransferServer is listening */
    private String ipAddress;

    /** Port number where the peer's FileTransferServer is listening */
    private int port;

    /**
     * Current number of active outbound file transfers (used for load balancing)
     */
    private int load;

    /** Timestamp of the last heartbeat signal received by the central server */
    private long lastHeartbeat;

    public User(String username, String ipAddress, int port) {
        this.username = username;
        this.ipAddress = ipAddress;
        this.port = port;
        this.load = 0;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getLoad() {
        return load;
    }

    public void setLoad(int load) {
        this.load = load;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", port=" + port +
                '}';
    }
}
