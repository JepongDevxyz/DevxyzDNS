package com.jepongdevxyz.devxyzdns;

/**
 * IPv4 + UDP parsing and DNS-response packet building.
 * Pure Java so it can be unit-tested on a JVM.
 */
public final class PacketUtil {
    private PacketUtil() {}

    public static final class IpInfo {
        public int headerLen;  // bytes
        public int protocol;
        public int srcIp;
        public int dstIp;
        public int totalLen;
    }

    public static final class UdpInfo {
        public int srcPort;
        public int dstPort;
        public int payloadOffset;
        public int payloadLen;
    }

    public static IpInfo parseIPv4(byte[] b, int len) {
        if (b == null || len < 20) return null;
        int verIhl = b[0] & 0xFF;
        if ((verIhl >> 4) != 4) return null;
        int ihl = (verIhl & 0x0F) * 4;
        if (ihl < 20 || len < ihl) return null;
        IpInfo ip = new IpInfo();
        ip.headerLen = ihl;
        ip.totalLen = readShort(b, 2);
        if (ip.totalLen <= 0 || ip.totalLen > len) ip.totalLen = len;
        ip.protocol = b[9] & 0xFF;
        ip.srcIp = readInt(b, 12);
        ip.dstIp = readInt(b, 16);
        return ip;
    }

    public static UdpInfo parseUdp(byte[] b, int ipHeaderLen, int totalLen) {
        if (totalLen < ipHeaderLen + 8) return null;
        UdpInfo u = new UdpInfo();
        int o = ipHeaderLen;
        u.srcPort = readShort(b, o);
        u.dstPort = readShort(b, o + 2);
        int udpLen = readShort(b, o + 4);
        u.payloadOffset = o + 8;
        u.payloadLen = Math.min(udpLen - 8, totalLen - u.payloadOffset);
        if (u.payloadLen < 0) return null;
        return u;
    }

    /**
     * Build a full IPv4+UDP response packet for a DNS answer.
     * Source/destination IPs and ports are swapped relative to the query packet.
     */
    public static byte[] buildResponse(byte[] queryPkt, int queryLen, byte[] dnsPayload) {
        IpInfo ip = parseIPv4(queryPkt, queryLen);
        if (ip == null) throw new IllegalArgumentException("bad IP packet");
        UdpInfo udp = parseUdp(queryPkt, ip.headerLen, queryLen);
        if (udp == null) throw new IllegalArgumentException("bad UDP packet");

        final int IP_H = 20, UDP_H = 8;
        int total = IP_H + UDP_H + dnsPayload.length;
        byte[] out = new byte[total];

        // IPv4 header
        out[0] = 0x45; // version 4, IHL 5
        out[1] = 0;    // DSCP/ECN
        writeShort(out, 2, total);
        writeShort(out, 4, 0);      // identification
        writeShort(out, 6, 0x4000); // flags: don't fragment
        out[8] = 64;                // TTL
        out[9] = 17;                // protocol UDP
        writeShort(out, 10, 0);     // checksum placeholder
        writeInt(out, 12, ip.dstIp); // src = original dst (the fake DNS IP)
        writeInt(out, 16, ip.srcIp); // dst = original src (the client)
        writeShort(out, 10, ipChecksum(out, 0, IP_H));

        // UDP header
        int o = IP_H;
        writeShort(out, o, udp.dstPort);     // src port = original dst port (53)
        writeShort(out, o + 2, udp.srcPort); // dst port = original src port
        writeShort(out, o + 4, UDP_H + dnsPayload.length);
        writeShort(out, o + 6, 0);           // checksum placeholder
        System.arraycopy(dnsPayload, 0, out, o + UDP_H, dnsPayload.length);
        writeShort(out, o + 6, udpChecksum(ip.dstIp, ip.srcIp, out, o, UDP_H + dnsPayload.length));
        return out;
    }

    public static int ipChecksum(byte[] b, int off, int len) {
        long sum = 0;
        int i = off;
        while (i + 1 < off + len) {
            sum += ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
            i += 2;
        }
        if (i < off + len) sum += (b[i] & 0xFF) << 8;
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }

    public static int udpChecksum(int srcIp, int dstIp, byte[] udp, int off, int len) {
        long sum = 0;
        // pseudo-header
        sum += (srcIp >> 16) & 0xFFFF; sum += srcIp & 0xFFFF;
        sum += (dstIp >> 16) & 0xFFFF; sum += dstIp & 0xFFFF;
        sum += 17; // protocol UDP
        sum += len;
        int i = off;
        while (i + 1 < off + len) {
            sum += ((udp[i] & 0xFF) << 8) | (udp[i + 1] & 0xFF);
            i += 2;
        }
        if (i < off + len) sum += (udp[i] & 0xFF) << 8;
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        int c = (int) (~sum & 0xFFFF);
        return c == 0 ? 0xFFFF : c; // 0 means "no checksum" in UDP; never send 0
    }

    public static int readShort(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    public static void writeShort(byte[] b, int o, int v) {
        b[o] = (byte) ((v >> 8) & 0xFF);
        b[o + 1] = (byte) (v & 0xFF);
    }

    public static int readInt(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16)
                | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    public static void writeInt(byte[] b, int o, int v) {
        b[o] = (byte) ((v >> 24) & 0xFF);
        b[o + 1] = (byte) ((v >> 16) & 0xFF);
        b[o + 2] = (byte) ((v >> 8) & 0xFF);
        b[o + 3] = (byte) (v & 0xFF);
    }

    public static int ipToInt(String dotted) {
        String[] p = dotted.trim().split("\\.");
        if (p.length != 4) throw new IllegalArgumentException("bad IPv4: " + dotted);
        int v = 0;
        for (String s : p) {
            int n = Integer.parseInt(s.trim());
            if (n < 0 || n > 255) throw new IllegalArgumentException("bad IPv4: " + dotted);
            v = (v << 8) | n;
        }
        return v;
    }

    public static String intToIp(int v) {
        return ((v >> 24) & 0xFF) + "." + ((v >> 16) & 0xFF) + "."
                + ((v >> 8) & 0xFF) + "." + (v & 0xFF);
    }

    /** Build a minimal DNS query for an A record (used by tests). */
    public static byte[] buildTestQuery(int id, String name) {
        String[] labels = name.split("\\.");
        int qnameLen = 1;
        for (String l : labels) qnameLen += 1 + l.length();
        byte[] q = new byte[12 + qnameLen + 4];
        writeShort(q, 0, id);
        writeShort(q, 2, 0x0100); // standard query, recursion desired
        writeShort(q, 4, 1);      // QDCOUNT
        int o = 12;
        for (String l : labels) {
            q[o++] = (byte) l.length();
            for (char c : l.toCharArray()) q[o++] = (byte) c;
        }
        q[o++] = 0;
        writeShort(q, o, 1);     // QTYPE A
        writeShort(q, o + 2, 1); // QCLASS IN
        return q;
    }

    // ---------------- IPv6 ----------------

    public static final class Ip6Info {
        public int nextHeader;
        public final byte[] src = new byte[16];
        public final byte[] dst = new byte[16];
    }

    /** Parse a 40-byte IPv6 header. Returns null when invalid. */
    public static Ip6Info parseIPv6(byte[] b, int len) {
        if (b == null || len < 40) return null;
        if (((b[0] & 0xFF) >> 4) != 6) return null;
        Ip6Info ip = new Ip6Info();
        ip.nextHeader = b[6] & 0xFF;
        // Note: extension headers are not followed; DNS/UDP packets have none.
        System.arraycopy(b, 8, ip.src, 0, 16);
        System.arraycopy(b, 24, ip.dst, 0, 16);
        return ip;
    }

    /** Build an IPv6+UDP response packet with addresses/ports swapped. */
    public static byte[] buildResponse6(byte[] queryPkt, int queryLen, byte[] dnsPayload) {
        Ip6Info ip = parseIPv6(queryPkt, queryLen);
        if (ip == null) throw new IllegalArgumentException("bad IPv6 packet");
        if (ip.nextHeader != 17) throw new IllegalArgumentException("not UDP");
        UdpInfo udp = parseUdp(queryPkt, 40, queryLen);
        if (udp == null) throw new IllegalArgumentException("bad UDP packet");

        final int IP6_H = 40, UDP_H = 8;
        int udpLen = UDP_H + dnsPayload.length;
        byte[] out = new byte[IP6_H + udpLen];

        out[0] = 0x60; // version 6
        // bytes 1-3: traffic class + flow label = 0
        writeShort(out, 4, udpLen); // payload length
        out[6] = 17;  // next header UDP
        out[7] = 64;  // hop limit
        System.arraycopy(ip.dst, 0, out, 8, 16);  // src = original dst
        System.arraycopy(ip.src, 0, out, 24, 16); // dst = original src

        int o = IP6_H;
        writeShort(out, o, udp.dstPort);
        writeShort(out, o + 2, udp.srcPort);
        writeShort(out, o + 4, udpLen);
        writeShort(out, o + 6, 0);
        System.arraycopy(dnsPayload, 0, out, o + UDP_H, dnsPayload.length);
        writeShort(out, o + 6, udpChecksum6(out, 8, out, o, udpLen));
        return out;
    }

    /** UDP checksum with the IPv6 pseudo-header. srcOff points at the 16-byte src in the packet. */
    public static int udpChecksum6(byte[] pkt, int srcOff, byte[] udp, int off, int len) {
        long sum = 0;
        for (int i = 0; i < 16; i += 2) {
            sum += ((pkt[srcOff + i] & 0xFF) << 8) | (pkt[srcOff + i + 1] & 0xFF);
            sum += ((pkt[srcOff + 16 + i] & 0xFF) << 8) | (pkt[srcOff + 16 + i + 1] & 0xFF);
        }
        sum += (len >> 16) & 0xFFFF;
        sum += len & 0xFFFF;
        sum += 17; // next header UDP
        int i = off;
        while (i + 1 < off + len) {
            sum += ((udp[i] & 0xFF) << 8) | (udp[i + 1] & 0xFF);
            i += 2;
        }
        if (i < off + len) sum += (udp[i] & 0xFF) << 8;
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        int c = (int) (~sum & 0xFFFF);
        return c == 0 ? 0xFFFF : c;
    }

    /** Wrap a DNS query payload in a full IPv4+UDP packet (for tests). */
    public static byte[] wrapQuery4(byte[] dns, int srcIp, int dstIp, int srcPort) {
        byte[] pkt = new byte[20 + 8 + dns.length];
        pkt[0] = 0x45;
        writeShort(pkt, 2, pkt.length);
        writeShort(pkt, 6, 0x4000);
        pkt[8] = 64; pkt[9] = 17;
        writeInt(pkt, 12, srcIp);
        writeInt(pkt, 16, dstIp);
        writeShort(pkt, 10, ipChecksum(pkt, 0, 20));
        int o = 20;
        writeShort(pkt, o, srcPort);
        writeShort(pkt, o + 2, 53);
        writeShort(pkt, o + 4, 8 + dns.length);
        writeShort(pkt, o + 6, 0);
        System.arraycopy(dns, 0, pkt, o + 8, dns.length);
        writeShort(pkt, o + 6, udpChecksum(srcIp, dstIp, pkt, o, 8 + dns.length));
        return pkt;
    }

    /** Wrap a DNS query payload in a full IPv6+UDP packet (for tests). */
    public static byte[] wrapQuery6(byte[] dns, byte[] srcIp16, byte[] dstIp16, int srcPort) {
        byte[] pkt = new byte[40 + 8 + dns.length];
        pkt[0] = 0x60;
        writeShort(pkt, 4, 8 + dns.length);
        pkt[6] = 17; pkt[7] = 64;
        System.arraycopy(srcIp16, 0, pkt, 8, 16);
        System.arraycopy(dstIp16, 0, pkt, 24, 16);
        int o = 40;
        writeShort(pkt, o, srcPort);
        writeShort(pkt, o + 2, 53);
        writeShort(pkt, o + 4, 8 + dns.length);
        writeShort(pkt, o + 6, 0);
        System.arraycopy(dns, 0, pkt, o + 8, dns.length);
        writeShort(pkt, o + 6, udpChecksum6(pkt, 8, pkt, o, 8 + dns.length));
        return pkt;
    }
}
