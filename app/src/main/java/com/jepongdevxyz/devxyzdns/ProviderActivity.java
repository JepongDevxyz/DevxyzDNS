package com.jepongdevxyz.devxyzdns;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Select DNS Provider screen with filter dialog. */
public class ProviderActivity extends AppCompatActivity {

    private ProviderAdapter adapter;
    private boolean showPublic = true, showCustom = true;
    private int sortMode = 0; // 0=default, 1=asc, 2=desc

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnFilter).setOnClickListener(v -> showFilterDialog());
        findViewById(R.id.fabAdd).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, CustomDnsActivity.class)));

        RecyclerView rv = findViewById(R.id.rvProviders);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProviderAdapter(this, new ProviderAdapter.Listener() {
            @Override public void onSelect(DnsProvider p) {
                Prefs.setProvider(ProviderActivity.this, p);
                Toast.makeText(ProviderActivity.this, "Selected: " + p.name, Toast.LENGTH_SHORT).show();
                reload();
            }
            @Override public void onDelete(DnsProvider p) {
                ProviderStore.removeCustom(ProviderActivity.this, p);
                if (p.equals(Prefs.getProvider(ProviderActivity.this))) Prefs.setProvider(ProviderActivity.this, null);
                Toast.makeText(ProviderActivity.this, "Deleted: " + p.name, Toast.LENGTH_SHORT).show();
                reload();
            }
        });
        rv.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        List<DnsProvider> all = ProviderStore.all(this);
        List<DnsProvider> out = new ArrayList<>();
        for (DnsProvider p : all) {
            if (p.custom && !showCustom) continue;
            if (!p.custom && !showPublic) continue;
            out.add(p);
        }
        if (sortMode == 1) Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        else if (sortMode == 2) Collections.sort(out, (a, b) -> b.name.compareToIgnoreCase(a.name));
        adapter.setData(out);
    }

    private void showFilterDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_server_filter, null);
        CheckBox cbPublic = v.findViewById(R.id.cbPublic);
        CheckBox cbCustom = v.findViewById(R.id.cbCustom);
        RadioGroup rg = v.findViewById(R.id.rgSort);
        cbPublic.setChecked(showPublic);
        cbCustom.setChecked(showCustom);
        rg.check(sortMode == 1 ? R.id.rbAsc : sortMode == 2 ? R.id.rbDesc : R.id.rbDefault);

        final boolean[] pub = {showPublic};
        final boolean[] cus = {showCustom};
        final int[] sort = {sortMode};
        cbPublic.setOnCheckedChangeListener((b, c) -> pub[0] = c);
        cbCustom.setOnCheckedChangeListener((b, c) -> cus[0] = c);
        rg.setOnCheckedChangeListener((g, id) ->
                sort[0] = id == R.id.rbAsc ? 1 : id == R.id.rbDesc ? 2 : 0);

        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setView(v).create();
        v.findViewById(R.id.btnClose).setOnClickListener(x -> d.dismiss());
        v.findViewById(R.id.btnApply).setOnClickListener(x -> {
            showPublic = pub[0];
            showCustom = cus[0];
            sortMode = sort[0];
            reload();
            d.dismiss();
        });
        d.show();
    }
}
