package com.jepongdevxyz.devxyzdns;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

/**
 * Plain DNS (port 53) client: UDP with TCP fallback on truncated replies.
 * Pure Java so it can be unit-tested on a JVM.
 */
public class PlainDnsClient {

    private final SocketProtector protector;

    public PlainDnsClient(SocketProtector protector) {
        this.protector = protector;
    }

    /** Query each server in order over UDP; fall back to TCP if the reply is truncated. */
    public byte[] query(byte[] dnsQuery, List<InetAddress> servers, int timeoutMs) throws IOException {
        IOException last = null;
        for (InetAddress server : servers) {
            try {
                return queryUdp(dnsQuery, server, timeoutMs);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("no DNS servers configured");
    }

    private byte[] queryUdp(byte[] q, InetAddress server, int timeoutMs) throws IOException {
        DatagramSocket ds = new DatagramSocket();
        try {
            protector.protect(ds);
            ds.setSoTimeout(timeoutMs);
            ds.send(new DatagramPacket(q, q.length, server, 53));
            byte[] buf = new byte[4096];
            DatagramPacket rp = new DatagramPacket(buf, buf.length);
            ds.receive(rp);
            byte[] resp = Arrays.copyOf(rp.getData(), rp.getLength());
            if (isTruncated(resp)) {
                return queryTcp(q, server, timeoutMs);
            }
            return resp;
        } finally {
            ds.close();
        }
    }

    private static boolean isTruncated(byte[] dns) {
        return dns != null && dns.length >= 3 && (dns[2] & 0x02) != 0; // TC bit
    }

    private byte[] queryTcp(byte[] q, InetAddress server, int timeoutMs) throws IOException {
        Socket sock = new Socket();
        try {
            protector.protect(sock);
            sock.connect(new InetSocketAddress(server, 53), timeoutMs);
            sock.setSoTimeout(timeoutMs);
            DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            DataInputStream in = new DataInputStream(sock.getInputStream());
            out.writeShort(q.length);
            out.write(q);
            out.flush();
            int len = in.readUnsignedShort();
            if (len <= 0 || len > 65535) throw new IOException("bad TCP DNS length");
            byte[] resp = new byte[len];
            in.readFully(resp);
            return resp;
        } finally {
            try { sock.close(); } catch (IOException ignored) {}
        }
    }
}
