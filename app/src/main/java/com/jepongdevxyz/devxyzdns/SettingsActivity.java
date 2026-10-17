package com.jepongdevxyz.devxyzdns;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Settings screen with working toggles + backup/restore. */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_IMPORT = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.rowLanguage).setOnClickListener(v -> showLanguageDialog());

        SwitchMaterial swBoot = findViewById(R.id.swBoot);
        SwitchMaterial swDeep = findViewById(R.id.swDeep);
        SwitchMaterial swNotif = findViewById(R.id.swNotif);
        SwitchMaterial swAuto = findViewById(R.id.swAuto);

        swBoot.setChecked(Prefs.startOnBoot(this));
        swDeep.setChecked(Prefs.deepTest(this));
        swNotif.setChecked(Prefs.persistentNotif(this));
        swAuto.setChecked(Prefs.autoChange(this));

        swBoot.setOnCheckedChangeListener((b, c) -> Prefs.setStartOnBoot(this, c));
        swDeep.setOnCheckedChangeListener((b, c) -> Prefs.setDeepTest(this, c));
        swNotif.setOnCheckedChangeListener((b, c) -> {
            Prefs.setPersistentNotif(this, c);
            toast(c ? "Notification will show while connected" : "Notification off");
        });
        swAuto.setOnCheckedChangeListener((b, c) -> Prefs.setAutoChange(this, c));

        findViewById(R.id.rowExport).setOnClickListener(v -> exportSettings());
        findViewById(R.id.rowImport).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            startActivityForResult(i, REQ_IMPORT);
        });
    }

    private void showLanguageDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.language))
                .setMessage("Language is set to Auto and follows your system language. DevxyzDNS is currently available in English.")
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void exportSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            toast("Backup needs Android 10 or newer");
            return;
        }
        try {
            JSONObject root = new JSONObject();
            DnsProvider sel = Prefs.getProvider(this);
            root.put("selected", sel == null ? JSONObject.NULL : sel.toJson());
            JSONArray custom = new JSONArray();
            for (DnsProvider p : ProviderStore.custom(this)) custom.put(p.toJson());
            root.put("custom", custom);
            JSONObject t = new JSONObject();
            t.put("boot", Prefs.startOnBoot(this));
            t.put("deep", Prefs.deepTest(this));
            t.put("notif", Prefs.persistentNotif(this));
            t.put("auto", Prefs.autoChange(this));
            root.put("toggles", t);
            JSONArray excl = new JSONArray();
            for (String s : Prefs.excludedApps(this)) excl.put(s);
            root.put("excluded", excl);

            byte[] data = root.toString(2).getBytes(StandardCharsets.UTF_8);
            String name = "devxyzdns-backup.json";
            ContentResolver cr = getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            }
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) { toast("Export failed"); return; }
            try (OutputStream os = cr.openOutputStream(uri)) {
                os.write(data);
            }
            toast("Saved to Downloads/" + name);
        } catch (Exception e) {
            toast("Export failed: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importSettings(data.getData());
        }
    }

    private void importSettings(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            byte[] buf = new byte[is.available()];
            int n = 0, r;
            while ((r = is.read(buf, n, buf.length - n)) > 0) n += r;
            JSONObject root = new JSONObject(new String(buf, 0, n, StandardCharsets.UTF_8));

            JSONArray custom = root.optJSONArray("custom");
            if (custom != null) {
                for (int i = 0; i < custom.length(); i++) {
                    ProviderStore.addCustom(this, DnsProvider.fromJson(custom.getJSONObject(i)));
                }
            }
            if (!root.isNull("selected")) {
                Prefs.setProvider(this, DnsProvider.fromJson(root.getJSONObject("selected")));
            }
            JSONObject t = root.optJSONObject("toggles");
            if (t != null) {
                Prefs.setStartOnBoot(this, t.optBoolean("boot", false));
                Prefs.setDeepTest(this, t.optBoolean("deep", false));
                Prefs.setPersistentNotif(this, t.optBoolean("notif", false));
                Prefs.setAutoChange(this, t.optBoolean("auto", false));
            }
            JSONArray excl = root.optJSONArray("excluded");
            if (excl != null) {
                java.util.Set<String> s = new java.util.HashSet<>();
                for (int i = 0; i < excl.length(); i++) s.add(excl.getString(i));
                Prefs.setExcludedApps(this, s);
            }
            toast("Settings imported");
            recreate();
        } catch (Exception e) {
            toast("Import failed: " + e.getMessage());
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
