package com.jepongdevxyz.devxyzdns;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Bundled public providers (IPs verified against official docs) plus
 * user-added custom providers persisted in SharedPreferences.
 */
public final class ProviderStore {
    private ProviderStore() {}

    private static final String NAME = "devxyzdns_providers";
    private static final String K_CUSTOM = "custom_list";

    public static List<DnsProvider> bundled() {
        List<DnsProvider> l = new ArrayList<>();
        l.add(new DnsProvider("Cloudflare DNS", "1.1.1.1", "1.0.0.1"));
        l.add(new DnsProvider("Google DNS", "8.8.8.8", "8.8.4.4"));
        l.add(new DnsProvider("Quad9", "9.9.9.9", "149.112.112.112"));
        l.add(new DnsProvider("OpenDNS", "208.67.222.222", "208.67.220.220"));
        l.add(new DnsProvider("OpenDNS (Family Shield)", "208.67.222.123", "208.67.220.123"));
        l.add(new DnsProvider("Comodo Secure DNS", "8.26.56.26", "8.20.247.20"));
        l.add(new DnsProvider("Cisco Umbrella", "208.67.222.222", "208.67.220.220"));
        l.add(new DnsProvider("Control D", "76.76.2.0", "76.76.10.0"));
        l.add(new DnsProvider("Control D (Family Friendly)", "76.76.2.3", "76.76.10.3"));
        l.add(new DnsProvider("CleanBrowsing", "185.228.168.9", "185.228.169.9"));
        l.add(new DnsProvider("CleanBrowsing (Family Shield)", "185.228.168.168", "185.228.169.168"));
        l.add(new DnsProvider("CIRA Canadian Shield", "149.112.121.10", "149.112.122.10"));
        l.add(new DnsProvider("DNS.Watch", "84.200.69.80", "84.200.70.40"));
        l.add(new DnsProvider("DNS.SB", "185.222.222.222", "45.11.45.11"));
        l.add(new DnsProvider("French Data Network (FDN)", "80.67.169.12", "80.67.169.40"));
        l.add(new DnsProvider("Level3/Lumen DNS", "4.2.2.1", "4.2.2.2"));
        l.add(new DnsProvider("Neustar UltraDNS", "156.154.70.1", "156.154.71.1"));
        l.add(new DnsProvider("Neustar UltraDNS (Safe)", "156.154.70.2", "156.154.71.2"));
        l.add(new DnsProvider("Neustar UltraDNS (Family Shield)", "156.154.70.3", "156.154.71.3"));
        l.add(new DnsProvider("SafeDNS", "195.46.39.39", "195.46.39.40"));
        return l;
    }

    public static List<DnsProvider> custom(Context c) {
        List<DnsProvider> l = new ArrayList<>();
        SharedPreferences sp = c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        String raw = sp.getString(K_CUSTOM, "[]");
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                l.add(DnsProvider.fromJson(a.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return l;
    }

    public static List<DnsProvider> all(Context c) {
        List<DnsProvider> l = bundled();
        l.addAll(custom(c));
        return l;
    }

    public static void addCustom(Context c, DnsProvider p) {
        p.custom = true;
        List<DnsProvider> l = custom(c);
        l.add(p);
        save(c, l);
    }

    public static void removeCustom(Context c, DnsProvider p) {
        List<DnsProvider> l = custom(c);
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).equals(p)) { l.remove(i); break; }
        }
        save(c, l);
    }

    private static void save(Context c, List<DnsProvider> l) {
        JSONArray a = new JSONArray();
        for (DnsProvider p : l) a.put(p.toJson());
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .edit().putString(K_CUSTOM, a.toString()).apply();
    }
}
