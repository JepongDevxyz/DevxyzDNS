package com.jepongdevxyz.devxyzdns;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Speed test results, fastest first. */
public class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.VH> {

    public static class Row {
        DnsProvider provider;
        long ms;
        boolean ok;
    }

    public interface Listener {
        void onUse(Row r);
    }

    private final Context ctx;
    private final Listener listener;
    private List<Row> data = new ArrayList<>();

    public ResultAdapter(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    public void setData(List<Row> d) {
        data = d;
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvMs;
        Button btnUse;
        VH(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
            tvMs = v.findViewById(R.id.tvMs);
            btnUse = v.findViewById(R.id.btnUse);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_result, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row r = data.get(position);
        h.tvName.setText(r.provider.name);
        if (r.ok) {
            h.tvMs.setText(r.ms + " ms");
            h.tvMs.setTextColor(0xFF43D14E);
            h.btnUse.setAlpha(1.0f);
            h.btnUse.setEnabled(true);
        } else {
            h.tvMs.setText("no response");
            h.tvMs.setTextColor(0xFF8B96AB);
            h.btnUse.setAlpha(0.4f);
            h.btnUse.setEnabled(false);
        }
        h.btnUse.setOnClickListener(v -> listener.onUse(r));
    }

    @Override
    public int getItemCount() { return data.size(); }
}
