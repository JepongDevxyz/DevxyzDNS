package com.jepongdevxyz.devxyzdns;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DNS-only VPN (IPv4 + IPv6): declares fake DNS servers and routes only
 * those /32 + /128 into the tunnel, so every DNS query lands here while all
 * other traffic bypasses the VPN untouched. Queries are forwarded upstream —
 * DNS-over-TLS when the provider has a hostname, plain UDP (TCP fallback)
 * for IPs — and answers are written back into the tunnel.
 */
public class DnsVpnService extends VpnService {

    private static final String TAG = "DevxyzDNS";
    public static final String ACTION_STOP = "com.jepongdevxyz.devxyzdns.STOP";

    private static final String VPN_ADDRESS4 = "10.0.0.2";
    private static final String FAKE_DNS4 = "10.0.0.3";
    private static final int FAKE_DNS4_INT = PacketUtil.ipToInt(FAKE_DNS4);
    private static final String VPN_ADDRESS6 = "fd00:db8:1::2";
    private static final String FAKE_DNS6 = "fd00:db8:1::3";
    private static final byte[] FAKE_DNS6_BYTES = parseIp6(FAKE_DNS6);

    private static final String NOTIF_CH = "devxyzdns_status";
    private static final int NOTIF_ID = 71;

    private static volatile boolean running = false;
    private static volatile String runningProvider = "";

    public static boolean isRunning() { return running; }
    public static String runningProvider() { return runningProvider; }

    private ParcelFileDescriptor tun;
    private Thread worker;
    private Thread autoThread;
    private volatile boolean stop;
    private ExecutorService pool;
    private FileOutputStream tunOut;
    private final Object tunWriteLock = new Object();

    private final SocketProtector protector = new SocketProtector() {
        @Override public void protect(Socket s) { DnsVpnService.this.protect(s); }
        @Override public void protect(DatagramSocket s) { DnsVpnService.this.protect(s); }
    };

    private static byte[] parseIp6(String s) {
        try {
            byte[] b = InetAddress.getByName(s).getAddress();
            if (b.length == 16) return b;
        } catch (Exception ignored) {}
        return new byte[16];
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopVpn();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_STICKY;
        try {
            startVpn();
        } catch (Exception e) {
            Log.e(TAG, "start failed", e);
            stopSelf();
        }
        return START_STICKY;
    }

    private void startVpn() throws IOException {
        DnsProvider provider = Prefs.getProvider(this);
        if (provider == null) throw new IOException("no DNS provider selected");

        final boolean useDot = provider.hostname != null && !provider.hostname.isEmpty();
        final List<InetAddress> servers = new ArrayList<>();
        if (!useDot) {
            try {
                if (!provider.ip1.isEmpty()) servers.add(InetAddress.getByName(provider.ip1));
                if (!provider.ip2.isEmpty()) servers.add(InetAddress.getByName(provider.ip2));
            } catch (Exception e) {
                throw new IOException("bad DNS IP: " + e.getMessage());
            }
            if (servers.isEmpty()) throw new IOException("no DNS IPs configured");
        }

        Builder b = new Builder();
        b.setSession("DevxyzDNS");
        b.addAddress(VPN_ADDRESS4, 32);
        b.addAddress(VPN_ADDRESS6, 128);
        b.addDnsServer(FAKE_DNS4);
        b.addDnsServer(FAKE_DNS6);
        b.addRoute(FAKE_DNS4, 32);   // only DNS enters the tunnel
        b.addRoute(FAKE_DNS6, 128);
        // Per-app exclusions: their traffic (incl. DNS) bypasses the VPN.
        Set<String> excluded = Prefs.excludedApps(this);
        for (String pkg : excluded) {
            try { b.addDisallowedApplication(pkg); }
            catch (Exception e) { Log.w(TAG, "cannot exclude " + pkg); }
        }
        // Never loop our own traffic.
        try { b.addDisallowedApplication(getPackageName()); } catch (Exception ignored) {}

        tun = b.establish();
        if (tun == null) throw new IOException("establish() returned null");

        final DotClient dot = useDot ? new DotClient(provider.hostname, protector) : null;
        final PlainDnsClient plain = !useDot ? new PlainDnsClient(protector) : null;

        stop = false;
        pool = Executors.newFixedThreadPool(8);
        tunOut = new FileOutputStream(tun.getFileDescriptor());
        running = true;
        runningProvider = provider.name;

        worker = new Thread(() -> {
            FileInputStream in = new FileInputStream(tun.getFileDescriptor());
            java.nio.channels.FileChannel ch = in.getChannel();
            ByteBuffer buf = ByteBuffer.allocate(32767);
            try {
                while (!stop) {
                    buf.clear();
                    int len = ch.read(buf);
                    if (len <= 0) continue;
                    final byte[] pkt = new byte[len];
                    System.arraycopy(buf.array(), 0, pkt, 0, len);
                    pool.execute(() -> handlePacket(pkt, dot, plain, servers, useDot));
                }
            } catch (IOException e) {
                if (!stop) Log.e(TAG, "tun read failed", e);
            }
        }, "DevxyzDNS-tun");
        worker.setDaemon(true);
        worker.start();

        if (Prefs.persistentNotif(this)) showNotification(provider.name);
        if (Prefs.autoChange(this)) startAutoChange();

        Log.i(TAG, "VPN started, mode=" + (useDot ? "DoT(" + provider.hostname + ")" : "plain"));
    }

    private void handlePacket(byte[] pkt, DotClient dot, PlainDnsClient plain,
                              List<InetAddress> servers, boolean useDot) {
        try {
            int version = (pkt[0] & 0xFF) >> 4;
            byte[] query;
            boolean v6;
            if (version == 4) {
                PacketUtil.IpInfo ip = PacketUtil.parseIPv4(pkt, pkt.length);
                if (ip == null || ip.protocol != 17) return;
                PacketUtil.UdpInfo udp = PacketUtil.parseUdp(pkt, ip.headerLen, pkt.length);
                if (udp == null || udp.dstPort != 53 || udp.payloadLen < 12) return;
                if (ip.dstIp != FAKE_DNS4_INT) return;
                query = new byte[udp.payloadLen];
                System.arraycopy(pkt, udp.payloadOffset, query, 0, udp.payloadLen);
                v6 = false;
            } else if (version == 6) {
                PacketUtil.Ip6Info ip = PacketUtil.parseIPv6(pkt, pkt.length);
                if (ip == null || ip.nextHeader != 17) return;
                PacketUtil.UdpInfo udp = PacketUtil.parseUdp(pkt, 40, pkt.length);
                if (udp == null || udp.dstPort != 53 || udp.payloadLen < 12) return;
                if (!java.util.Arrays.equals(ip.dst, FAKE_DNS6_BYTES)) return;
                query = new byte[udp.payloadLen];
                System.arraycopy(pkt, udp.payloadOffset, query, 0, udp.payloadLen);
                v6 = true;
            } else {
                return;
            }

            byte[] answer = useDot
                    ? dot.query(query, 5000)
                    : plain.query(query, servers, 3000);
            if (answer == null || answer.length < 12) return;

            byte[] resp = v6
                    ? PacketUtil.buildResponse6(pkt, pkt.length, answer)
                    : PacketUtil.buildResponse(pkt, pkt.length, answer);
            synchronized (tunWriteLock) {
                tunOut.write(resp);
            }
        } catch (Exception e) {
            Log.w(TAG, "packet handling failed: " + e.getMessage());
        }
    }

    /** Periodically re-test and hop to a faster provider while running. */
    private void startAutoChange() {
        autoThread = new Thread(() -> {
            try {
                while (!stop) {
                    Thread.sleep(15 * 60 * 1000);
                    if (stop) break;
                    List<DnsProvider> all = ProviderStore.all(this);
                    long bestMs = Long.MAX_VALUE;
                    DnsProvider best = null;
                    for (DnsProvider p : all) {
                        SpeedTester.Result r = SpeedTester.test(p, 1, 3000, protector);
                        if (r.ok && r.ms < bestMs) { bestMs = r.ms; best = p; }
                    }
                    DnsProvider cur = Prefs.getProvider(this);
                    if (best != null && cur != null && !best.equals(cur)) {
                        Log.i(TAG, "auto-change: " + cur.name + " -> " + best.name);
                        Prefs.setProvider(this, best);
                        // restart the tunnel with the new provider
                        stopVpn();
                        try { startVpn(); } catch (Exception e) { Log.e(TAG, "auto restart failed", e); }
                        break;
                    }
                }
            } catch (InterruptedException ignored) {}
        }, "DevxyzDNS-auto");
        autoThread.setDaemon(true);
        autoThread.start();
    }

    private void showNotification(String providerName) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(NOTIF_CH, "DevxyzDNS",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Intent stopI = new Intent(this, DnsVpnService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopI,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent openI = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openI,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder nb = new Notification.Builder(this, NOTIF_CH)
                .setContentTitle("DevxyzDNS active")
                .setContentText(providerName)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(null, "Stop", stopPi).build())
                .setOngoing(true);
        nm.notify(NOTIF_ID, nb.build());
    }

    private void hideNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIF_ID);
    }

    private void stopVpn() {
        stop = true;
        running = false;
        runningProvider = "";
        try {
            if (worker != null) worker.interrupt();
            if (autoThread != null) autoThread.interrupt();
            if (pool != null) pool.shutdownNow();
            if (tunOut != null) tunOut.close();
            if (tun != null) tun.close();
        } catch (IOException ignored) {}
        tun = null;
        hideNotification();
        Log.i(TAG, "VPN stopped");
    }

    @Override
    public void onRevoke() {
        stopVpn();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
