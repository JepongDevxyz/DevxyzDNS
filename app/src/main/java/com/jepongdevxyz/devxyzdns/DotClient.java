package com.jepongdevxyz.devxyzdns;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * DNS-over-TLS (RFC 7858) client: forwards DNS wire-format queries to
 * hostname:853 over a single persistent TLS connection.
 *
 * Query transaction IDs are rewritten to locally-unique values so
 * concurrent queries can never collide; the original ID is restored
 * on the way back. Pure Java (javax.net.ssl) so it runs on JVM tests too.
 */
public class DotClient {

    private static final int DOT_PORT = 853;

    private final String hostname;
    private final SocketProtector protector;

    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Pending> pending = new ConcurrentHashMap<>();
    private final Object connectLock = new Object();
    private final Object writeLock = new Object();

    private volatile SSLSocket socket;
    private volatile DataInputStream in;
    private volatile DataOutputStream out;
    private volatile Thread reader;

    private static final class Pending {
        final int origId;
        final CompletableFuture<byte[]> future;
        Pending(int origId, CompletableFuture<byte[]> future) {
            this.origId = origId;
            this.future = future;
        }
    }

    public DotClient(String hostname, SocketProtector protector) {
        if (hostname == null || hostname.trim().isEmpty())
            throw new IllegalArgumentException("empty DoT hostname");
        this.hostname = hostname.trim();
        this.protector = protector;
    }

    /** Send one DNS query, return the raw DNS response. Retries once with a reconnect. */
    public byte[] query(byte[] dnsQuery, int timeoutMs) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return queryOnce(dnsQuery, timeoutMs);
            } catch (IOException e) {
                last = e;
                closeQuietly(); // force a fresh TLS connection next attempt
            }
        }
        throw last;
    }

    private byte[] queryOnce(byte[] dnsQuery, int timeoutMs) throws IOException {
        if (dnsQuery == null || dnsQuery.length < 12)
            throw new IOException("bad DNS query");
        ensureConnected();

        int origId = PacketUtil.readShort(dnsQuery, 0);
        int newId;
        do {
            newId = nextId.getAndIncrement() & 0xFFFF;
        } while (newId == 0);

        byte[] q = dnsQuery.clone();
        PacketUtil.writeShort(q, 0, newId);

        CompletableFuture<byte[]> f = new CompletableFuture<>();
        pending.put(newId, new Pending(origId, f));
        try {
            synchronized (writeLock) {
                if (out == null) throw new IOException("not connected");
                out.writeShort(q.length);
                out.write(q);
                out.flush();
            }
        } catch (IOException e) {
            pending.remove(newId);
            throw e;
        }

        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.remove(newId);
            throw new IOException("DoT query timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(newId);
            throw new IOException("interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException("DoT query failed", e.getCause());
        }
    }

    private void ensureConnected() throws IOException {
        synchronized (connectLock) {
            SSLSocket s = socket;
            if (s != null && s.isConnected() && !s.isClosed()) return;
            closeQuietly();

            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sock = (SSLSocket) factory.createSocket();
            protector.protect(sock);
            sock.connect(new InetSocketAddress(hostname, DOT_PORT), 10000);
            SSLParameters params = sock.getSSLParameters();
            List<SNIServerName> names = new ArrayList<>(1);
            names.add(new SNIHostName(hostname));
            params.setServerNames(names);
            sock.setSSLParameters(params);
            sock.startHandshake();
            sock.setSoTimeout(20000);

            socket = sock;
            in = new DataInputStream(sock.getInputStream());
            out = new DataOutputStream(sock.getOutputStream());
            startReader();
        }
    }

    private void startReader() {
        Thread t = new Thread(this::readLoop, "DevxyzDNS-DoT-reader");
        t.setDaemon(true);
        reader = t;
        t.start();
    }

    private void readLoop() {
        DataInputStream din = in;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                int len = din.readUnsignedShort();
                if (len <= 0 || len > 65535) throw new IOException("bad DoT frame length");
                byte[] msg = new byte[len];
                din.readFully(msg);
                int id = PacketUtil.readShort(msg, 0);
                Pending p = pending.remove(id);
                if (p != null) {
                    PacketUtil.writeShort(msg, 0, p.origId); // restore caller's ID
                    p.future.complete(msg);
                }
            }
        } catch (EOFException | java.net.SocketException e) {
            failAll(new IOException("DoT connection closed", e));
        } catch (IOException e) {
            failAll(e);
        } finally {
            closeQuietly();
        }
    }

    private void failAll(IOException e) {
        for (Map.Entry<Integer, Pending> en : pending.entrySet()) {
            Pending p = pending.remove(en.getKey());
            if (p != null) p.future.completeExceptionally(e);
        }
    }

    public void closeQuietly() {
        synchronized (connectLock) {
            Thread r = reader;
            reader = null;
            if (r != null) r.interrupt();
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
            socket = null;
            in = null;
            out = null;
        }
    }
}
