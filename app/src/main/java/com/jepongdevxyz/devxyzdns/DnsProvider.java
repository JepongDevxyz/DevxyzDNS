package com.jepongdevxyz.devxyzdns;

import org.json.JSONObject;

/** One DNS provider entry: bundled public or user custom. */
public class DnsProvider {
    public String name;
    public String ip1;
    public String ip2;
    public String hostname; // optional DoT hostname — when set, the VPN uses encrypted DNS
    public boolean custom;

    public DnsProvider(String name, String ip1, String ip2) {
        this(name, ip1, ip2, null, false);
    }

    public DnsProvider(String name, String ip1, String ip2, String hostname, boolean custom) {
        this.name = name;
        this.ip1 = ip1 == null ? "" : ip1;
        this.ip2 = ip2 == null ? "" : ip2;
        this.hostname = hostname == null ? "" : hostname;
        this.custom = custom;
    }

    public String typeLabel() {
        return custom ? "Custom" : "Public";
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("ip1", ip1);
            o.put("ip2", ip2);
            o.put("hostname", hostname);
            o.put("custom", custom);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static DnsProvider fromJson(JSONObject o) {
        return new DnsProvider(
                o.optString("name", "Custom"),
                o.optString("ip1", ""),
                o.optString("ip2", ""),
                o.optString("hostname", ""),
                o.optBoolean("custom", true));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DnsProvider)) return false;
        DnsProvider p = (DnsProvider) o;
        return name.equals(p.name) && ip1.equals(p.ip1) && ip2.equals(p.ip2)
                && hostname.equals(p.hostname);
    }
}
