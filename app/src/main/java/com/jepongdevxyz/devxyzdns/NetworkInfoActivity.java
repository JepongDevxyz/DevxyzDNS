package com.jepongdevxyz.devxyzdns;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Collections;

/** Network Info screen with real, live values. */
public class NetworkInfoActivity extends AppCompatActivity {

    private TextView tvDown, tvUp, tvStatus, tvProvider, tvConn, tvPublicIp, tvGateway, tvLocalIp;
    private volatile boolean sampling;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_info);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvDown = findViewById(R.id.tvDown);
        tvUp = findViewById(R.id.tvUp);
        tvStatus = findViewById(R.id.tvStatus);
        tvProvider = findViewById(R.id.tvProvider);
        tvConn = findViewById(R.id.tvConn);
        tvPublicIp = findViewById(R.id.tvPublicIp);
        tvGateway = findViewById(R.id.tvGateway);
        tvLocalIp = findViewById(R.id.tvLocalIp);

        fillStatic();
        startSpeedSampling();
        fetchPublicIp();
    }

    private void fillStatic() {
        boolean on = DnsVpnService.isRunning();
        tvStatus.setText(on ? "Connected" : "Not Connected");
        DnsProvider p = Prefs.getProvider(this);
        tvProvider.setText(p == null ? "Default (Not Connected)" : p.name + (on ? "" : " (Not Connected)"));
        tvConn.setText(connectionType());
        tvGateway.setText(gatewayIp());
        tvLocalIp.setText(localIp());
    }

    private String connectionType() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            if (n == null) return "None";
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            if (c == null) return "Unknown";
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Mobile data";
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "VPN";
            return "Other";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String gatewayIp() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            DhcpInfo d = wm.getDhcpInfo();
            if (d == null || d.gateway == 0) return "—";
            int g = d.gateway;
            return (g & 0xFF) + "." + ((g >> 8) & 0xFF) + "." + ((g >> 16) & 0xFF) + "." + ((g >> 24) & 0xFF);
        } catch (Exception e) {
            return "—";
        }
    }

    private String localIp() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            int ip = wi.getIpAddress();
            if (ip != 0) {
                return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
            }
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!a.isLoopbackAddress() && a.getAddress().length == 4) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "—";
    }

    private void fetchPublicIp() {
        new Thread(() -> {
            String ip = "—";
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://api.ipify.org").openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    ip = br.readLine().trim();
                }
            } catch (Exception ignored) {}
            final String f = ip;
            runOnUiThread(() -> tvPublicIp.setText(f));
        }, "DevxyzDNS-ipify").start();
    }

    private void startSpeedSampling() {
        sampling = true;
        new Thread(() -> {
            long lastRx = TrafficStats.getTotalRxBytes();
            long lastTx = TrafficStats.getTotalTxBytes();
            long lastT = System.currentTimeMillis();
            while (sampling) {
                try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
                long now = System.currentTimeMillis();
                long rx = TrafficStats.getTotalRxBytes();
                long tx = TrafficStats.getTotalTxBytes();
                double dt = Math.max(1, now - lastT) / 1000.0;
                final String d = "Download " + fmt((rx - lastRx) / dt) + "/s";
                final String u = "Upload " + fmt((tx - lastTx) / dt) + "/s";
                lastRx = rx; lastTx = tx; lastT = now;
                runOnUiThread(() -> { tvDown.setText(d); tvUp.setText(u); });
            }
        }, "DevxyzDNS-traffic").start();
    }

    private String fmt(double bps) {
        if (bps < 1024) return String.format("%.1f B", bps);
        if (bps < 1024 * 1024) return String.format("%.1f KB", bps / 1024);
        return String.format("%.1f MB", bps / 1024 / 1024);
    }

    @Override
    protected void onDestroy() {
        sampling = false;
        super.onDestroy();
    }
}
