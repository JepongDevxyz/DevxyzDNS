package com.jepongdevxyz.devxyzdns;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** All persisted app state. */
public final class Prefs {
    private Prefs() {}

    private static final String NAME = "devxyzdns";
    private static final String K_ONBOARDED = "onboarded";
    private static final String K_PROVIDER = "provider_json";
    private static final String K_BOOT = "boot";
    private static final String K_DEEP = "deep_test";
    private static final String K_NOTIF = "persistent_notif";
    private static final String K_AUTO = "auto_change";
    private static final String K_EXCLUDED = "excluded_apps";
    private static final String K_WHATSNEW = "whatsnew_seen";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static boolean isOnboarded(Context c) { return sp(c).getBoolean(K_ONBOARDED, false); }
    public static void setOnboarded(Context c) { sp(c).edit().putBoolean(K_ONBOARDED, true).apply(); }

    public static DnsProvider getProvider(Context c) {
        String raw = sp(c).getString(K_PROVIDER, null);
        if (raw == null) return null;
        try { return DnsProvider.fromJson(new JSONObject(raw)); }
        catch (Exception e) { return null; }
    }

    public static void setProvider(Context c, DnsProvider p) {
        if (p == null) sp(c).edit().remove(K_PROVIDER).apply();
        else sp(c).edit().putString(K_PROVIDER, p.toJson().toString()).apply();
    }

    // All toggles default OFF on first run (her standing rule).
    public static boolean startOnBoot(Context c) { return sp(c).getBoolean(K_BOOT, false); }
    public static void setStartOnBoot(Context c, boolean v) { sp(c).edit().putBoolean(K_BOOT, v).apply(); }

    public static boolean deepTest(Context c) { return sp(c).getBoolean(K_DEEP, false); }
    public static void setDeepTest(Context c, boolean v) { sp(c).edit().putBoolean(K_DEEP, v).apply(); }

    public static boolean persistentNotif(Context c) { return sp(c).getBoolean(K_NOTIF, false); }
    public static void setPersistentNotif(Context c, boolean v) { sp(c).edit().putBoolean(K_NOTIF, v).apply(); }

    public static boolean autoChange(Context c) { return sp(c).getBoolean(K_AUTO, false); }
    public static void setAutoChange(Context c, boolean v) { sp(c).edit().putBoolean(K_AUTO, v).apply(); }

    /** Packages excluded from the VPN (their DNS bypasses DevxyzDNS). */
    public static Set<String> excludedApps(Context c) {
        return new HashSet<>(sp(c).getStringSet(K_EXCLUDED, new HashSet<>()));
    }

    public static void setExcludedApps(Context c, Set<String> pkgs) {
        sp(c).edit().putStringSet(K_EXCLUDED, new HashSet<>(pkgs)).apply();
    }

    public static int whatsNewSeen(Context c) { return sp(c).getInt(K_WHATSNEW, 0); }
    public static void setWhatsNewSeen(Context c, int v) { sp(c).edit().putInt(K_WHATSNEW, v).apply(); }
}
