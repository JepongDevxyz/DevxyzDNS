package com.jepongdevxyz.devxyzdns;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Measures real DNS latency per provider by sending actual queries.
 * Pure Java (needs a SocketProtector; on-device the VPN service passes one
 * that bypasses the tunnel — for the standalone speed test no VPN is up,
 * so a no-op protector is fine).
 */
public final class SpeedTester {
    private SpeedTester() {}

    public static final class Result {
        public final DnsProvider provider;
        public final long ms;   // -1 when failed
        public final boolean ok;
        public Result(DnsProvider provider, long ms, boolean ok) {
            this.provider = provider;
            this.ms = ms;
            this.ok = ok;
        }
    }

    private static final SocketProtector NOP = new SocketProtector() {
        @Override public void protect(java.net.Socket s) {}
        @Override public void protect(java.net.DatagramSocket s) {}
    };

    /** Test one provider: `samples` queries, returns the best round-trip. */
    public static Result test(DnsProvider p, int samples, int timeoutMs) {
        return test(p, samples, timeoutMs, NOP);
    }

    public static Result test(DnsProvider p, int samples, int timeoutMs, SocketProtector protector) {
        long best = Long.MAX_VALUE;
        try {
            if (p.hostname != null && !p.hostname.isEmpty()) {
                DotClient dot = new DotClient(p.hostname, protector);
                try {
                    for (int i = 0; i < samples; i++) {
                        byte[] q = DnsMessage.buildQuery("example.com", 1);
                        long t = System.nanoTime();
                        byte[] r = dot.query(q, timeoutMs);
                        if (!DnsMessage.isResponse(r)) return new Result(p, -1, false);
                        best = Math.min(best, (System.nanoTime() - t) / 1_000_000);
                    }
                } finally {
                    dot.closeQuietly();
                }
            } else {
                List<InetAddress> servers = new ArrayList<>();
                if (p.ip1 != null && !p.ip1.isEmpty()) servers.add(InetAddress.getByName(p.ip1));
                if (p.ip2 != null && !p.ip2.isEmpty()) servers.add(InetAddress.getByName(p.ip2));
                if (servers.isEmpty()) return new Result(p, -1, false);
                PlainDnsClient plain = new PlainDnsClient(protector);
                for (int i = 0; i < samples; i++) {
                    byte[] q = DnsMessage.buildQuery("example.com", 1);
                    long t = System.nanoTime();
                    byte[] r = plain.query(q, servers, timeoutMs);
                    if (!DnsMessage.isResponse(r)) return new Result(p, -1, false);
                    best = Math.min(best, (System.nanoTime() - t) / 1_000_000);
                }
            }
        } catch (Exception e) {
            return new Result(p, -1, false);
        }
        return best == Long.MAX_VALUE ? new Result(p, -1, false) : new Result(p, best, true);
    }
}
