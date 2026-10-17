package com.jepongdevxyz.devxyzdns;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** DNS Lookup: query any hostname against any provider, show A/AAAA answers. */
public class LookupActivity extends AppCompatActivity {

    private List<DnsProvider> providers;
    private AutoCompleteTextView spProvider;
    private EditText etHost;
    private TextView tvResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lookup);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnGear).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, SettingsActivity.class)));

        spProvider = findViewById(R.id.spProvider);
        etHost = findViewById(R.id.etHost);
        tvResults = findViewById(R.id.tvResults);

        providers = ProviderStore.all(this);
        List<String> names = new ArrayList<>();
        for (DnsProvider p : providers) names.add(p.name);
        spProvider.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, names));
        DnsProvider sel = Prefs.getProvider(this);
        spProvider.setText(sel != null ? sel.name : names.get(0), false);

        findViewById(R.id.btnLookup).setOnClickListener(v -> doLookup());
    }

    private void doLookup() {
        String host = etHost.getText().toString().trim();
        if (host.isEmpty()) { toast("Enter a hostname or IP"); return; }
        int idx = indexOf(spProvider.getText().toString());
        if (idx < 0) { toast("Pick a DNS server"); return; }
        final DnsProvider p = providers.get(idx);
        tvResults.setText("Looking up " + host + " via " + p.name + "…");
        tvResults.setGravity(android.view.Gravity.START);

        new Thread(() -> {
            final String out = runLookup(p, host);
            runOnUiThread(() -> tvResults.setText(out));
        }, "DevxyzDNS-lookup").start();
    }

    private String runLookup(DnsProvider p, String host) {
        try {
            List<InetAddress> servers = new ArrayList<>();
            PlainDnsClient plain = null;
            DotClient dot = null;
            boolean useDot = !p.hostname.isEmpty();
            if (useDot) {
                dot = new DotClient(p.hostname, new SocketProtector() {
                    @Override public void protect(java.net.Socket s) {}
                    @Override public void protect(java.net.DatagramSocket s) {}
                });
            } else {
                if (!p.ip1.isEmpty()) servers.add(InetAddress.getByName(p.ip1));
                if (!p.ip2.isEmpty()) servers.add(InetAddress.getByName(p.ip2));
                if (servers.isEmpty()) return "No DNS IPs for " + p.name;
                final PlainDnsClient pc = new PlainDnsClient(new SocketProtector() {
                    @Override public void protect(java.net.Socket s) {}
                    @Override public void protect(java.net.DatagramSocket s) {}
                });
                plain = pc;
            }
            StringBuilder sb = new StringBuilder();
            for (int qtype : new int[]{1, 28}) {
                byte[] q = DnsMessage.buildQuery(host, qtype);
                byte[] r = useDot ? dot.query(q, 8000) : plain.query(q, servers, 5000);
                List<String> ans = DnsMessage.parseAnswers(r);
                sb.append(qtype == 1 ? "A records:\n" : "\nAAAA records:\n");
                if (ans.isEmpty()) sb.append("  (none)\n");
                for (String a : ans) sb.append("  ").append(a).append("\n");
            }
            if (dot != null) dot.closeQuietly();
            return sb.toString().trim();
        } catch (Exception e) {
            return "Lookup failed: " + e.getMessage();
        }
    }

    private int indexOf(String name) {
        for (int i = 0; i < providers.size(); i++) {
            if (providers.get(i).name.equals(name)) return i;
        }
        return -1;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
