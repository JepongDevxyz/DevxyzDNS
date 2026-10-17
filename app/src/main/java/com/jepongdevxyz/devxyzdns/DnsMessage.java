package com.jepongdevxyz.devxyzdns;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DNS wire-format helpers: query building + answer parsing (A/AAAA).
 * Pure Java so it can be unit-tested on a JVM.
 */
public final class DnsMessage {
    private DnsMessage() {}

    private static final AtomicInteger ID = new AtomicInteger(0x1000);

    /** Build a standard recursive query. qtype: 1=A, 28=AAAA. */
    public static byte[] buildQuery(String name, int qtype) {
        int id = ID.incrementAndGet() & 0xFFFF;
        return buildQuery(id, name, qtype);
    }

    public static byte[] buildQuery(int id, String name, int qtype) {
        String[] labels = name.split("\\.");
        int qnameLen = 1;
        for (String l : labels) qnameLen += 1 + l.length();
        byte[] q = new byte[12 + qnameLen + 4];
        PacketUtil.writeShort(q, 0, id);
        PacketUtil.writeShort(q, 2, 0x0100); // standard query, RD=1
        PacketUtil.writeShort(q, 4, 1);      // QDCOUNT=1
        int o = 12;
        for (String l : labels) {
            q[o++] = (byte) l.length();
            for (int i = 0; i < l.length(); i++) q[o++] = (byte) l.charAt(i);
        }
        q[o++] = 0;
        PacketUtil.writeShort(q, o, qtype);
        PacketUtil.writeShort(q, o + 2, 1); // IN
        return q;
    }

    /** Skip a (possibly compressed) domain name; returns offset after it. */
    private static int skipName(byte[] b, int o) {
        int jumps = 0;
        while (true) {
            if (o >= b.length) throw new IllegalArgumentException("truncated name");
            int len = b[o] & 0xFF;
            if ((len & 0xC0) == 0xC0) {
                o += 2; // compression pointer
                if (++jumps > 32) throw new IllegalArgumentException("pointer loop");
                break; // pointer always ends the name
            } else if (len == 0) {
                o += 1;
                break;
            } else {
                o += 1 + len;
            }
        }
        return o;
    }

    /** Extract A/AAAA answer records as strings. Never throws. */
    public static List<String> parseAnswers(byte[] resp) {
        List<String> out = new ArrayList<>();
        try {
            if (resp == null || resp.length < 12) return out;
            int qd = PacketUtil.readShort(resp, 4);
            int an = PacketUtil.readShort(resp, 6);
            int o = 12;
            for (int i = 0; i < qd; i++) {
                o = skipName(resp, o);
                o += 4; // QTYPE + QCLASS
            }
            for (int i = 0; i < an; i++) {
                o = skipName(resp, o);
                if (o + 10 > resp.length) break;
                int type = PacketUtil.readShort(resp, o);
                int rdlen = PacketUtil.readShort(resp, o + 8);
                o += 10;
                if (o + rdlen > resp.length) break;
                if (type == 1 && rdlen == 4) {
                    out.add((resp[o] & 0xFF) + "." + (resp[o + 1] & 0xFF) + "."
                            + (resp[o + 2] & 0xFF) + "." + (resp[o + 3] & 0xFF));
                } else if (type == 28 && rdlen == 16) {
                    StringBuilder sb = new StringBuilder();
                    for (int g = 0; g < 8; g++) {
                        if (g > 0) sb.append(':');
                        sb.append(Integer.toHexString(PacketUtil.readShort(resp, o + g * 2)));
                    }
                    out.add(sb.toString());
                }
                o += rdlen;
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** True if the response has QR set (i.e. it is a real DNS response). */
    public static boolean isResponse(byte[] resp) {
        return resp != null && resp.length >= 3 && (resp[2] & 0x80) != 0;
    }
}
