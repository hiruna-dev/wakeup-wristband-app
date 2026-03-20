package com.example.wristbandapp;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Unified RecyclerView adapter for the main dashboard list.
 * Handles two view types: saved locations (TYPE_LOCATION) and alarms (TYPE_ALARM).
 */
public class DashboardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onDeleteClick(DashboardItem item);
        void onEditClick(DashboardItem item);
    }

    private List<DashboardItem> items;
    private final OnItemClickListener itemListener;

    public DashboardAdapter(List<DashboardItem> items, OnItemClickListener itemListener) {
        this.items          = items;
        this.itemListener   = itemListener;
    }

    public void setItems(List<DashboardItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    // ── ViewHolder creation ────────────────────────────────────

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == DashboardItem.TYPE_ALARM) {
            View view = inflater.inflate(R.layout.item_alarm, parent, false);
            return new AlarmViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_location, parent, false);
            return new LocationViewHolder(view);
        }
    }

    // ── Binding ────────────────────────────────────────────────

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DashboardItem item = items.get(position);
        if (holder instanceof AlarmViewHolder) {
            bindAlarm((AlarmViewHolder) holder, item);
        } else {
            bindLocation((LocationViewHolder) holder, item);
        }
    }

    private void bindLocation(LocationViewHolder h, DashboardItem item) {
        h.tvName.setText(item.name);
        h.tvCoords.setText(String.format("Lat: %.5f, Lng: %.5f", item.latitude, item.longitude));
        h.tvRadius.setText("Radius: " + item.radiusMeters + "m");
        h.btnDelete.setOnClickListener(v -> {
            if (itemListener != null) itemListener.onDeleteClick(item);
        });
        if (h.btnViewMap != null) {
            h.btnViewMap.setOnClickListener(v -> {
                Context ctx = v.getContext();
                Intent intent = new Intent(ctx, MapPickerActivity.class);
                intent.putExtra("focusLat",  item.latitude);
                intent.putExtra("focusLng",  item.longitude);
                intent.putExtra("focusName", item.name);
                ctx.startActivity(intent);
            });
        }
    }

    private void bindAlarm(AlarmViewHolder h, DashboardItem item) {
        h.tvName.setText(item.name.isEmpty() ? "Alarm" : item.name);
        h.tvTime.setText(item.getFormattedTime());
        String days = (item.repeatDays == null || item.repeatDays.isEmpty())
                ? "Once" : item.repeatDays;
        h.tvRepeat.setText(days);
        h.btnDelete.setOnClickListener(v -> {
            if (itemListener != null) itemListener.onDeleteClick(item);
        });
        h.itemView.setOnClickListener(v -> {
            if (itemListener != null) itemListener.onEditClick(item);
        });
    }

    // ── ViewHolders ────────────────────────────────────────────

    static class LocationViewHolder extends RecyclerView.ViewHolder {
        TextView    tvName, tvCoords, tvRadius;
        ImageButton btnDelete;
        Button      btnViewMap;

        LocationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName    = itemView.findViewById(R.id.tvName);
            tvCoords  = itemView.findViewById(R.id.tvCoords);
            tvRadius  = itemView.findViewById(R.id.tvRadius);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnViewMap= itemView.findViewById(R.id.btnViewMap);
        }
    }

    static class AlarmViewHolder extends RecyclerView.ViewHolder {
        TextView    tvName, tvTime, tvRepeat;
        ImageButton btnDelete;

        AlarmViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName    = itemView.findViewById(R.id.tvName);
            tvTime    = itemView.findViewById(R.id.tvTime);
            tvRepeat  = itemView.findViewById(R.id.tvRepeat);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
