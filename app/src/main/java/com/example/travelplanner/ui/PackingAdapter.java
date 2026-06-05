package com.example.travelplanner.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.travelplanner.R;
import com.example.travelplanner.data.model.PackingItem;

import java.util.List;

/**
 * Simple adapter that binds PackingItem rows to a checkbox + label.
 * The host (Activity) supplies a listener so it can ask the ViewModel
 * to flip the packed state and refresh the progress counter.
 */
public class PackingAdapter extends RecyclerView.Adapter<PackingAdapter.VH> {

    public interface OnCheckedChangeListener {
        void onChecked(int position, boolean isChecked);
    }

    private List<PackingItem> items;
    private final OnCheckedChangeListener listener;

    public PackingAdapter(List<PackingItem> items, OnCheckedChangeListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void updateItems(List<PackingItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_packing, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        PackingItem item = items.get(position);
        holder.tvName.setText(item.getName());

        // Temporarily detach to avoid firing the listener while recycling.
        holder.cb.setOnCheckedChangeListener(null);
        holder.cb.setChecked(item.isPacked());
        holder.cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onChecked(holder.getAdapterPosition(), isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final CheckBox cb;
        final TextView tvName;

        VH(@NonNull View itemView) {
            super(itemView);
            cb = itemView.findViewById(R.id.cbItem);
            tvName = itemView.findViewById(R.id.tvItemName);
        }
    }
}
