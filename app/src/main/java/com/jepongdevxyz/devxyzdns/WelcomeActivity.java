package com.jepongdevxyz.devxyzdns;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** First-run welcome screen like the reference app. */
public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Prefs.isOnboarded(this)) {
            goMain();
            return;
        }
        setContentView(R.layout.activity_welcome);

        TextView tvAgree = findViewById(R.id.tvAgree);
        String pre = getString(R.string.agree_prefix);
        String tos = getString(R.string.tos);
        String and = getString(R.string.and);
        String pp = getString(R.string.privacy);
        SpannableString ss = new SpannableString(pre + tos + and + pp);
        int s1 = pre.length(), e1 = s1 + tos.length();
        int s2 = e1 + and.length(), e2 = s2 + pp.length();
        ss.setSpan(new UnderlineSpan(), s1, e1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new UnderlineSpan(), s2, e2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(link(v -> openLegal(true)), s1, e1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(link(v -> openLegal(false)), s2, e2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvAgree.setText(ss);
        tvAgree.setMovementMethod(LinkMovementMethod.getInstance());

        findViewById(R.id.btnStart).setOnClickListener(v -> {
            Prefs.setOnboarded(this);
            goMain();
        });
    }

    private ClickableSpan link(final View.OnClickListener l) {
        return new ClickableSpan() {
            @Override public void onClick(View widget) { l.onClick(widget); }
        };
    }

    private void openLegal(boolean tos) {
        Intent i = new Intent(this, LegalActivity.class);
        i.putExtra(LegalActivity.EXTRA_TOS, tos);
        startActivity(i);
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
