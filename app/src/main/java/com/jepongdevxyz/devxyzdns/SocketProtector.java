package com.jepongdevxyz.devxyzdns;

import java.net.DatagramSocket;
import java.net.Socket;

/**
 * Lets the VPN service shield outbound sockets from its own tunnel
 * (otherwise upstream queries would loop back into the VPN).
 * Pure Java so it can be unit-tested on a JVM.
 */
public interface SocketProtector {
    void protect(Socket s);
    void protect(DatagramSocket s);
}
