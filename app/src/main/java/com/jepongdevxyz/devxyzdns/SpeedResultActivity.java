package com.jepongdevxyz.devxyzdns;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Shows speed test results sorted fastest-first. */
public class SpeedResultActivity extends AppCompatActivity {

    public static final String EXTRA_RESULTS = "results";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speed_result);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvResults);
        rv.setLayoutManager(new LinearLayoutManager(this));
        ResultAdapter adapter = new ResultAdapter(this, r -> {
            Prefs.setProvider(this, r.provider);
            Toast.makeText(this, "Now using: " + r.provider.name, Toast.LENGTH_SHORT).show();
            finish();
        });
        rv.setAdapter(adapter);
        adapter.setData(parse(getIntent().getStringExtra(EXTRA_RESULTS)));
    }

    private List<ResultAdapter.Row> parse(String raw) {
        List<ResultAdapter.Row> out = new ArrayList<>();
        if (raw == null) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ResultAdapter.Row r = new ResultAdapter.Row();
                r.provider = DnsProvider.fromJson(o.getJSONObject("provider"));
                r.ms = o.optLong("ms", -1);
                r.ok = o.optBoolean("ok", false);
                out.add(r);
            }
        } catch (Exception ignored) {}
        return out;
    }
}
