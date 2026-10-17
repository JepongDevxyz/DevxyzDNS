package com.jepongdevxyz.devxyzdns;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Provider rows with expandable details. */
public class ProviderAdapter extends RecyclerView.Adapter<ProviderAdapter.VH> {

    public interface Listener {
        void onSelect(DnsProvider p);
        void onDelete(DnsProvider p);
    }

    private final Context ctx;
    private final Listener listener;
    private List<DnsProvider> data = new ArrayList<>();
    private int expanded = -1;

    public ProviderAdapter(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    public void setData(List<DnsProvider> d) {
        data = d;
        expanded = -1;
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivChev, ivCheck;
        TextView tvName, tvType, tvIps, tvHostname;
        LinearLayout detail;
        Button btnUse, btnDelete;
        VH(View v) {
            super(v);
            ivChev = v.findViewById(R.id.ivChev);
            ivCheck = v.findViewById(R.id.ivCheck);
            tvName = v.findViewById(R.id.tvName);
            tvType = v.findViewById(R.id.tvType);
            tvIps = v.findViewById(R.id.tvIps);
            tvHostname = v.findViewById(R.id.tvHostname);
            detail = v.findViewById(R.id.detail);
            btnUse = v.findViewById(R.id.btnUse);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_provider, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DnsProvider p = data.get(position);
        h.tvName.setText(p.name);
        h.tvType.setText(p.typeLabel());

        DnsProvider sel = Prefs.getProvider(ctx);
        boolean isSel = p.equals(sel);
        h.ivCheck.setAlpha(isSel ? 1.0f : 0.35f);

        boolean exp = position == expanded;
        h.detail.setVisibility(exp ? View.VISIBLE : View.GONE);
        h.ivChev.setRotation(exp ? 180f : 0f);

        StringBuilder ips = new StringBuilder();
        if (!p.ip1.isEmpty()) ips.append(p.ip1);
        if (!p.ip2.isEmpty()) ips.append(ips.length() > 0 ? "\n" : "").append(p.ip2);
        h.tvIps.setText(ips.toString());
        h.tvIps.setVisibility(ips.length() > 0 ? View.VISIBLE : View.GONE);
        if (!p.hostname.isEmpty()) {
            h.tvHostname.setText("DoT: " + p.hostname);
            h.tvHostname.setVisibility(View.VISIBLE);
        } else {
            h.tvHostname.setVisibility(View.GONE);
        }
        h.btnDelete.setVisibility(p.custom ? View.VISIBLE : View.GONE);

        View.OnClickListener toggle = v -> {
            int pos = h.getAdapterPosition();
            expanded = (expanded == pos) ? -1 : pos;
            notifyDataSetChanged();
        };
        h.ivChev.setOnClickListener(toggle);
        h.tvName.setOnClickListener(toggle);
        h.ivCheck.setOnClickListener(v -> listener.onSelect(p));
        h.btnUse.setOnClickListener(v -> listener.onSelect(p));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(p));
    }

    @Override
    public int getItemCount() { return data.size(); }
}
