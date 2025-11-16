package com.cache.cluster;

import com.cache.util.SerializationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Receives cache coordination messages from peer nodes.
 * Runs a TCP server to accept incoming messages with proper resource management.
 */
@Component
public class MessageReceiver {
    private static final Logger logger = LoggerFactory.getLogger(MessageReceiver.class);
    
    private final int port;
    private final ClusterCoordinator clusterCoordinator;
    private final ExecutorService executor;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final int socketTimeoutMs = 10000;

    public MessageReceiver(int port, ClusterCoordinator clusterCoordinator) {
        this.port = port;
        this.clusterCoordinator = clusterCoordinator;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "message-receiver-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running) {
            logger.warn("Message receiver already running");
            return;
        }
        
        running = true;
        executor.submit(() -> {
            try {
                serverSocket = new ServerSocket(port);
                logger.info("Message receiver started on port {}", port);
                
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        clientSocket.setSoTimeout(socketTimeoutMs);
                        executor.submit(() -> handleClient(clientSocket));
                    } catch (IOException e) {
                        if (running) {
                            logger.error("Error accepting connection", e);
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Error starting message receiver", e);
            } finally {
                logger.info("Message receiver stopped");
            }
        });
    }

    private void handleClient(Socket socket) {
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            
            // Read message type
            int typeLength = in.readInt();
            byte[] typeBytes = new byte[typeLength];
            in.readFully(typeBytes);
            String messageType = new String(typeBytes, "UTF-8");
            
            // Read message
            int messageLength = in.readInt();
            byte[] messageBytes = new byte[messageLength];
            in.readFully(messageBytes);
            
            // Deserialize and handle
            if ("INVALIDATION".equals(messageType)) {
                InvalidationMessage message = SerializationUtil.deserialize(messageBytes, InvalidationMessage.class);
                clusterCoordinator.handleInvalidation(message);
                out.writeUTF("OK");
            } else if ("REPLICATION".equals(messageType)) {
                ReplicationMessage message = SerializationUtil.deserialize(messageBytes, ReplicationMessage.class);
                clusterCoordinator.handleReplication(message);
                out.writeUTF("OK");
            } else if ("HEARTBEAT".equals(messageType)) {
                HeartbeatMessage message = SerializationUtil.deserialize(messageBytes, HeartbeatMessage.class);
                clusterCoordinator.handleHeartbeat(message);
                out.writeUTF("OK");
            } else {
                logger.warn("Unknown message type: {}", messageType);
                out.writeUTF("ERROR");
            }
            
            out.flush();
        } catch (Exception e) {
            logger.error("Error handling client message", e);
        } finally {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                logger.debug("Error closing socket", e);
            }
        }
    }

    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error stopping message receiver", e);
        }
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("Message receiver executor did not terminate gracefully");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Message receiver stopped");
    }
}
