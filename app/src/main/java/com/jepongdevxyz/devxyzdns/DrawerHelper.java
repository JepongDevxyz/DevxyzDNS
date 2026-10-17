package com.jepongdevxyz.devxyzdns;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Wires the shared navigation drawer in every activity that has one. */
public final class DrawerHelper {
    private DrawerHelper() {}

    public static void wire(final Activity a, DrawerLayout drawer) {
        ImageButton btnMenu = a.findViewById(R.id.btnMenu);
        if (btnMenu != null) btnMenu.setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        ImageButton btnCrown = a.findViewById(R.id.btnCrown);
        if (btnCrown != null) btnCrown.setOnClickListener(v -> showFreeDialog(a));

        on(a, R.id.drawerFree, () -> showFreeDialog(a));
        on(a, R.id.drawerNetwork, () -> a.startActivity(new Intent(a, NetworkInfoActivity.class)));
        on(a, R.id.drawerCustom, () -> a.startActivity(new Intent(a, CustomDnsActivity.class)));
        on(a, R.id.drawerLookup, () -> a.startActivity(new Intent(a, LookupActivity.class)));
        on(a, R.id.drawerSettings, () -> a.startActivity(new Intent(a, SettingsActivity.class)));
        on(a, R.id.drawerShare, () -> shareApp(a));
        on(a, R.id.drawerFeedback, () -> sendFeedback(a));
        on(a, R.id.drawerRate, () -> showRateDialog(a));
        on(a, R.id.drawerAbout, () -> showAboutDialog(a));
    }

    private static void on(Activity a, int id, Runnable r) {
        View v = a.findViewById(id);
        if (v != null) v.setOnClickListener(v2 -> r.run());
    }

    private static void showFreeDialog(Activity a) {
        new MaterialAlertDialogBuilder(a)
                .setTitle("DevxyzDNS")
                .setMessage("Good news — every feature in DevxyzDNS is free. There is no premium version and nothing to unlock.\n\n" + a.getString(R.string.powered_by))
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private static void shareApp(Activity a) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, "Try DevxyzDNS — a fast, private DNS changer for Android (IPv4 & IPv6). " + a.getString(R.string.powered_by));
        a.startActivity(Intent.createChooser(i, "Share DevxyzDNS"));
    }

    private static void sendFeedback(Activity a) {
        Intent i = new Intent(Intent.ACTION_SENDTO);
        i.setData(Uri.parse("mailto:businessdevgithubjepong@gmail.com"));
        i.putExtra(Intent.EXTRA_SUBJECT, "DevxyzDNS feedback");
        try {
            a.startActivity(Intent.createChooser(i, "Send feedback"));
        } catch (Exception e) {
            Toast.makeText(a, "No email app found", Toast.LENGTH_SHORT).show();
        }
    }

    private static void showRateDialog(Activity a) {
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_rate, null);
        final int[] stars = {5};
        int[] ids = {R.id.star1, R.id.star2, R.id.star3, R.id.star4, R.id.star5};
        final ImageButton[] btns = new ImageButton[5];
        for (int i = 0; i < 5; i++) {
            btns[i] = v.findViewById(ids[i]);
            final int n = i + 1;
            btns[i].setOnClickListener(v2 -> {
                stars[0] = n;
                paintStars(btns, n);
            });
        }
        paintStars(btns, 5);
        new MaterialAlertDialogBuilder(a)
                .setTitle("Rate DevxyzDNS")
                .setView(v)
                .setPositiveButton("Submit", (d, w) -> {
                    a.getSharedPreferences("devxyzdns", 0).edit()
                            .putInt("rating", stars[0]).apply();
                    Toast.makeText(a, "Thanks for rating " + stars[0] + " stars!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void paintStars(ImageButton[] btns, int n) {
        for (int i = 0; i < btns.length; i++) {
            btns[i].setAlpha(i < n ? 1.0f : 0.3f);
        }
    }

    private static void showAboutDialog(Activity a) {
        String version;
        try {
            version = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "1.0";
        }
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_about, null);
        ((TextView) v.findViewById(R.id.tvAboutVersion)).setText("Version " + version);
        new MaterialAlertDialogBuilder(a)
                .setView(v)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
