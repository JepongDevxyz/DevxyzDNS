package com.jepongdevxyz.devxyzdns;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Per-app include/exclude list for the VPN. */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {

    public static class AppInfo {
        String label;
        String pkg;
        Drawable icon;
        boolean excluded;
    }

    public interface Listener {
        void onChanged();
    }

    private final Context ctx;
    private final Listener listener;
    private List<AppInfo> all = new ArrayList<>();
    private List<AppInfo> shown = new ArrayList<>();

    public AppAdapter(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    public void setData(List<AppInfo> d) {
        all = d;
        shown = new ArrayList<>(d);
        notifyDataSetChanged();
    }

    public void filter(String q, int mode) {
        shown = new ArrayList<>();
        String needle = q == null ? "" : q.toLowerCase().trim();
        for (AppInfo a : all) {
            if (!needle.isEmpty() && !a.label.toLowerCase().contains(needle)) continue;
            if (mode == 1 && a.excluded) continue;   // included only
            if (mode == 2 && !a.excluded) continue;  // excluded only
            shown.add(a);
        }
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon, ivInclude, ivExclude;
        TextView tvName;
        VH(View v) {
            super(v);
            ivIcon = v.findViewById(R.id.ivIcon);
            ivInclude = v.findViewById(R.id.ivInclude);
            ivExclude = v.findViewById(R.id.ivExclude);
            tvName = v.findViewById(R.id.tvName);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // root is vertical wrapper; row is child 0
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AppInfo a = shown.get(position);
        h.tvName.setText(a.label);
        h.ivIcon.setImageDrawable(a.icon);
        paintState(h, a);
        h.ivInclude.setOnClickListener(v -> setExcluded(a, false, h));
        h.ivExclude.setOnClickListener(v -> setExcluded(a, true, h));
    }

    private void paintState(VH h, AppInfo a) {
        // included = green check bright, x dim; excluded = opposite
        h.ivInclude.setAlpha(a.excluded ? 0.35f : 1.0f);
        h.ivExclude.setAlpha(a.excluded ? 1.0f : 0.35f);
    }

    private void setExcluded(AppInfo a, boolean excluded, VH h) {
        if (a.excluded == excluded) return;
        a.excluded = excluded;
        java.util.Set<String> s = Prefs.excludedApps(ctx);
        if (excluded) s.add(a.pkg); else s.remove(a.pkg);
        Prefs.setExcludedApps(ctx, s);
        paintState(h, a);
        listener.onChanged();
    }

    @Override
    public int getItemCount() { return shown.size(); }
}
