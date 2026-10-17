package com.jepongdevxyz.devxyzdns;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Main screen: Speed Test tab with the reference layout. */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_VPN = 100;
    private static final int VERSION_CODE = 1;

    private DrawerLayout drawerLayout;
    private TextView tvSelectedServer;
    private LinearLayout stateIdle, stateRunning;
    private SpeedCircleView speedCircle;
    private Button btnDns;
    private volatile boolean testing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Prefs.isOnboarded(this)) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }
        ProviderStore.seedPersonal(this); // no-op in public builds
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawerLayout);
        DrawerHelper.wire(this, drawerLayout);

        tvSelectedServer = findViewById(R.id.tvSelectedServer);
        stateIdle = findViewById(R.id.stateIdle);
        stateRunning = findViewById(R.id.stateRunning);
        speedCircle = findViewById(R.id.speedCircle);
        btnDns = findViewById(R.id.btnDns);

        findViewById(R.id.btnSelectServer).setOnClickListener(v ->
                startActivity(new Intent(this, ProviderActivity.class)));
        findViewById(R.id.btnBegin).setOnClickListener(v -> runSpeedTest());
        btnDns.setOnClickListener(v -> toggleVpn());

        findViewById(R.id.tabSpeed).setOnClickListener(v -> {});
        findViewById(R.id.tabApps).setOnClickListener(v ->
                startActivity(new Intent(this, AppsActivity.class)));

        paintTabs(true);
        maybeShowWhatsNew();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshServerPill();
        refreshDnsButton();
    }

    private void refreshServerPill() {
        DnsProvider p = Prefs.getProvider(this);
        if (p == null) {
            tvSelectedServer.setText(R.string.select_server);
            tvSelectedServer.setTextColor(0xFF8B96AB);
        } else {
            tvSelectedServer.setText(p.name);
            tvSelectedServer.setTextColor(0xFFF2F5FA);
        }
    }

    private void refreshDnsButton() {
        boolean on = DnsVpnService.isRunning();
        btnDns.setBackgroundResource(on ? R.drawable.circle_dns_on : R.drawable.circle_dns_idle);
        btnDns.setTextColor(on ? 0xFF43D14E : 0xFFF2F5FA);
    }

    private void toggleVpn() {
        if (DnsVpnService.isRunning()) {
            startService(new Intent(this, DnsVpnService.class).setAction(DnsVpnService.ACTION_STOP));
            toast("DevxyzDNS stopped");
            refreshDnsButton();
            return;
        }
        if (Prefs.getProvider(this) == null) {
            toast("Select a DNS server first");
            startActivity(new Intent(this, ProviderActivity.class));
            return;
        }
        Intent prep = VpnService.prepare(this);
        if (prep != null) {
            startActivityForResult(prep, REQ_VPN);
        } else {
            startVpnService();
        }
    }

    private void startVpnService() {
        startService(new Intent(this, DnsVpnService.class));
        toast("DevxyzDNS started — " + getString(R.string.powered_by));
        btnDns.postDelayed(this::refreshDnsButton, 700);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK) startVpnService();
            else toast("VPN permission denied");
        }
    }

    // ---------------- Speed test ----------------

    private void runSpeedTest() {
        if (testing) return;
        testing = true;
        stateIdle.setVisibility(View.GONE);
        stateRunning.setVisibility(View.VISIBLE);
        speedCircle.setProgress(0f);

        final boolean deep = Prefs.deepTest(this);
        new Thread(() -> {
            List<DnsProvider> providers = ProviderStore.all(this);
            List<SpeedTester.Result> results = new ArrayList<>();
            int samples = deep ? 3 : 1;
            for (int i = 0; i < providers.size(); i++) {
                SpeedTester.Result r = SpeedTester.test(providers.get(i), samples, 3000);
                results.add(r);
                final float p = (float) (i + 1) / providers.size();
                runOnUiThread(() -> speedCircle.setProgress(p));
            }
            Collections.sort(results, (a, b2) -> {
                if (a.ok && !b2.ok) return -1;
                if (!a.ok && b2.ok) return 1;
                return Long.compare(a.ms, b2.ms);
            });
            runOnUiThread(() -> {
                testing = false;
                stateRunning.setVisibility(View.GONE);
                stateIdle.setVisibility(View.VISIBLE);
                openResults(results);
            });
        }, "DevxyzDNS-speedtest").start();
    }

    private void openResults(List<SpeedTester.Result> results) {
        try {
            JSONArray arr = new JSONArray();
            for (SpeedTester.Result r : results) {
                JSONObject o = new JSONObject();
                o.put("provider", r.provider.toJson());
                o.put("ms", r.ms);
                o.put("ok", r.ok);
                arr.put(o);
            }
            Intent i = new Intent(this, SpeedResultActivity.class);
            i.putExtra(SpeedResultActivity.EXTRA_RESULTS, arr.toString());
            startActivity(i);
        } catch (Exception e) {
            toast("Speed test failed: " + e.getMessage());
        }
    }

    // ---------------- Tabs / dialogs ----------------

    private void paintTabs(boolean speedActive) {
        int active = 0xFF43D14E, idle = 0xFF8B96AB;
        ((ImageView) findViewById(R.id.ivTabSpeed)).setColorFilter(speedActive ? active : idle);
        ((TextView) findViewById(R.id.tvTabSpeed)).setTextColor(speedActive ? active : idle);
        ((ImageView) findViewById(R.id.ivTabApps)).setColorFilter(speedActive ? idle : active);
        ((TextView) findViewById(R.id.tvTabApps)).setTextColor(speedActive ? idle : active);
    }

    private void maybeShowWhatsNew() {
        if (Prefs.whatsNewSeen(this) >= VERSION_CODE) return;
        Prefs.setWhatsNewSeen(this, VERSION_CODE);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_whats_new, null);
        new MaterialAlertDialogBuilder(this)
                .setView(v)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
