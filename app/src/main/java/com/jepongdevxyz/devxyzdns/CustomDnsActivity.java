package com.jepongdevxyz.devxyzdns;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Add Custom DNS Server screen. */
public class CustomDnsActivity extends AppCompatActivity {

    private EditText etName, etDns1, etDns2, etHostname;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_dns);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        etName = findViewById(R.id.etName);
        etDns1 = findViewById(R.id.etDns1);
        etDns2 = findViewById(R.id.etDns2);
        etHostname = findViewById(R.id.etHostname);

        findViewById(R.id.btnClear).setOnClickListener(v -> {
            etName.setText(""); etDns1.setText(""); etDns2.setText(""); etHostname.setText("");
        });
        findViewById(R.id.btnAdd).setOnClickListener(v -> addServer());
    }

    private void addServer() {
        String name = etName.getText().toString().trim();
        String ip1 = etDns1.getText().toString().trim();
        String ip2 = etDns2.getText().toString().trim();
        String host = etHostname.getText().toString().trim();
        if (name.isEmpty()) { toast("Enter a name"); return; }
        if (ip1.isEmpty() && host.isEmpty()) { toast("Enter DNS 1 or a DoT hostname"); return; }
        if (!ip1.isEmpty() && !validIp(ip1)) { toast("DNS 1 is not a valid IP"); return; }
        if (!ip2.isEmpty() && !validIp(ip2)) { toast("DNS 2 is not a valid IP"); return; }

        DnsProvider p = new DnsProvider(name, ip1, ip2, host, true);
        ProviderStore.addCustom(this, p);
        toast("Added: " + name);
        finish();
    }

    private boolean validIp(String s) {
        try {
            java.net.InetAddress a = java.net.InetAddress.getByName(s);
            return a.getHostAddress().equals(s);
        } catch (Exception e) {
            return false;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
