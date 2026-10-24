package com.silentauto.controller;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Reserve a distinct source port before connecting to the local backend. */
public final class RpcConnection {
    private RpcConnection() {}
    public static Socket connect(String host, int port, int timeoutMs) throws IOException {
        for (int attempt = 0; attempt < 8; attempt++) {
            Socket socket = new Socket();
            try {
                socket.bind(new InetSocketAddress(0));
                // Without a listener, Linux can otherwise connect a loopback socket to itself.
                if (socket.getLocalPort() == port) {
                    socket.close();
                    continue;
                }
                socket.connect(new InetSocketAddress(host, port), timeoutMs);
                return socket;
            } catch (IOException failed) {
                socket.close();
                throw failed;
            }
        }
        throw new IOException("Cannot reserve a distinct RPC source port");
    }
}
