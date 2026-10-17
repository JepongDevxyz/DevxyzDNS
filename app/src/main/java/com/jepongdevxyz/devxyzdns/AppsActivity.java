package com.jepongdevxyz.devxyzdns;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Apps tab: per-app include/exclude for the VPN. */
public class AppsActivity extends AppCompatActivity {

    private AppAdapter adapter;
    private EditText etSearch;
    private TextView tvFilter;
    private int filterMode = 0; // 0=all, 1=included, 2=excluded
    private boolean searchOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);

        DrawerLayout drawer = findViewById(R.id.drawerLayout);
        DrawerHelper.wire(this, drawer);

        BottomNavHelper.wire(this, false, this::toggleVpn);

        etSearch = findViewById(R.id.etSearch);
        tvFilter = findViewById(R.id.tvFilter);

        findViewById(R.id.btnSearch).setOnClickListener(v -> toggleSearch());
        findViewById(R.id.filterRow).setOnClickListener(v -> showFilterDialog());
        findViewById(R.id.btnSelectAll).setOnClickListener(v -> setAll(false));
        findViewById(R.id.btnDeselectAll).setOnClickListener(v -> setAll(true));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        RecyclerView rv = findViewById(R.id.rvApps);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppAdapter(this, this::maybeRestartVpn);
        rv.setAdapter(adapter);

        loadApps();
    }

    private void toggleSearch() {
        searchOpen = !searchOpen;
        etSearch.setVisibility(searchOpen ? View.VISIBLE : View.GONE);
        if (!searchOpen) { etSearch.setText(""); applyFilter(); }
    }

    private void showFilterDialog() {
        String[] items = {"All Apps", "Included only", "Excluded only"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Filter")
                .setSingleChoiceItems(items, filterMode, (d, w) -> {
                    filterMode = w;
                    tvFilter.setText(items[w]);
                    applyFilter();
                    d.dismiss();
                })
                .show();
    }

    private void applyFilter() {
        if (adapter != null) adapter.filter(etSearch.getText().toString(), filterMode);
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(0);
            Set<String> excluded = Prefs.excludedApps(this);
            List<AppAdapter.AppInfo> list = new ArrayList<>();
            for (ApplicationInfo ai : installed) {
                if (ai.packageName.equals(getPackageName())) continue;
                AppAdapter.AppInfo info = new AppAdapter.AppInfo();
                info.pkg = ai.packageName;
                info.label = String.valueOf(pm.getApplicationLabel(ai));
                try { info.icon = pm.getApplicationIcon(ai); } catch (Exception e) { info.icon = null; }
                info.excluded = excluded.contains(ai.packageName);
                list.add(info);
            }
            Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
            runOnUiThread(() -> {
                adapter.setData(list);
                applyFilter();
            });
        }, "DevxyzDNS-apps").start();
    }

    private void setAll(boolean exclude) {
        // apply to currently loaded list
        new Thread(() -> {
            Set<String> s = Prefs.excludedApps(this);
            PackageManager pm = getPackageManager();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if (ai.packageName.equals(getPackageName())) continue;
                if (exclude) s.add(ai.packageName); else s.remove(ai.packageName);
            }
            Prefs.setExcludedApps(this, s);
            runOnUiThread(() -> {
                loadApps();
                toast(exclude ? "All apps excluded" : "All apps included");
                maybeRestartVpn();
            });
        }, "DevxyzDNS-setall").start();
    }

    /** Exclusions only take effect at tunnel build time → restart if running. */
    private void maybeRestartVpn() {
        if (!DnsVpnService.isRunning()) return;
        startService(new Intent(this, DnsVpnService.class).setAction(DnsVpnService.ACTION_STOP));
        new android.os.Handler(getMainLooper()).postDelayed(() ->
                startService(new Intent(this, DnsVpnService.class)), 800);
        toast("Restarting DevxyzDNS to apply…");
    }

    private void toggleVpn() {
        if (DnsVpnService.isRunning()) {
            startService(new Intent(this, DnsVpnService.class).setAction(DnsVpnService.ACTION_STOP));
            toast("DevxyzDNS stopped");
            return;
        }
        if (Prefs.getProvider(this) == null) {
            toast("Select a DNS server first");
            startActivity(new Intent(this, ProviderActivity.class));
            return;
        }
        Intent prep = VpnService.prepare(this);
        if (prep != null) startActivityForResult(prep, 100);
        else {
            startService(new Intent(this, DnsVpnService.class));
            toast("DevxyzDNS started — " + getString(R.string.powered_by));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            startService(new Intent(this, DnsVpnService.class));
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
