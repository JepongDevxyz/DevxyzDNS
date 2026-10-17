package com.jepongdevxyz.devxyzdns;

import android.os.Bundle;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Terms of Service / Privacy Policy viewer (local assets, honest text). */
public class LegalActivity extends AppCompatActivity {

    public static final String EXTRA_TOS = "tos";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_legal);

        boolean tos = getIntent().getBooleanExtra(EXTRA_TOS, true);
        ((TextView) findViewById(R.id.tvTitle)).setText(tos ? "Terms of Service" : "Privacy Policy");
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        WebView wv = findViewById(R.id.web);
        wv.setBackgroundColor(0x000C1626);
        wv.loadUrl("file:///android_asset/" + (tos ? "tos.html" : "privacy.html"));
    }
}
