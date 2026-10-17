package com.jepongdevxyz.devxyzdns;

import android.app.Activity;
import android.content.Intent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/** Shared bottom-nav wiring: tab colors, navigation, center DNS toggle. */
public final class BottomNavHelper {
    private BottomNavHelper() {}

    public interface DnsToggle { void onToggle(); }

    /**
     * @param onMainTab true when the current screen IS the Speed Test tab
     *                  (so its tab does nothing and Apps navigates forward).
     */
    public static void wire(final Activity a, boolean onMainTab, final DnsToggle dnsToggle) {
        int active = 0xFF43D14E, idle = 0xFF8B96AB;
        boolean speedActive = onMainTab;
        ((ImageView) a.findViewById(R.id.ivTabSpeed)).setColorFilter(speedActive ? active : idle);
        ((TextView) a.findViewById(R.id.tvTabSpeed)).setTextColor(speedActive ? active : idle);
        ((ImageView) a.findViewById(R.id.ivTabApps)).setColorFilter(speedActive ? idle : active);
        ((TextView) a.findViewById(R.id.tvTabApps)).setTextColor(speedActive ? idle : active);

        a.findViewById(R.id.tabSpeed).setOnClickListener(v -> {
            if (!onMainTab) {
                a.finish(); // back to MainActivity
            }
        });
        a.findViewById(R.id.tabApps).setOnClickListener(v -> {
            if (onMainTab) a.startActivity(new Intent(a, AppsActivity.class));
        });

        Button btnDns = a.findViewById(R.id.btnDns);
        refreshDns(a, btnDns);
        btnDns.setOnClickListener(v -> {
            dnsToggle.onToggle();
            btnDns.postDelayed(() -> refreshDns(a, btnDns), 700);
        });
    }

    public static void refreshDns(Activity a, Button btnDns) {
        boolean on = DnsVpnService.isRunning();
        btnDns.setBackgroundResource(on ? R.drawable.circle_dns_on : R.drawable.circle_dns_idle);
        btnDns.setTextColor(on ? 0xFF43D14E : 0xFFF2F5FA);
    }
}
