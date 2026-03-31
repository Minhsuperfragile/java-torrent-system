package com.distributed.client;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

 
/**
 * Remote interface for the PeerDaemon.
 * Allows external processes (like PeerCLI) to control the daemon via RMI.
 */
public interface ClientInterface extends Remote {
     
    /**
     * Triggers a file download for the specified filename.
     */
    void downloadFile(String filename) throws RemoteException;

    /**
     * Retrieves the map of all files and their providers from the central server.
     */
    Map<String, List<String>> getFileLocations() throws RemoteException;

    /**
     * Returns the username associated with this daemon instance.
     */
    String getUsername() throws RemoteException;

    /**
     * Gracefully shuts down the daemon process.
     */
    void shutdown() throws RemoteException;
}
