package com.example.wristbandapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.ViewHolder> {
    private List<LocationItem> locations;
    private OnDeleteClickListener deleteClickListener;

    public interface OnDeleteClickListener {
        void onDeleteClick(LocationItem item);
    }

    public LocationAdapter(List<LocationItem> locations, OnDeleteClickListener listener) {
        this.locations = locations;
        this.deleteClickListener = listener;
    }

    public void setLocations(List<LocationItem> locations) {
        this.locations = locations;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_location, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocationItem item = locations.get(position);
        holder.tvName.setText(item.name);
        holder.tvCoords.setText(String.format("Lat: %.5f, Lng: %.5f", item.latitude, item.longitude));
        holder.tvRadius.setText("Radius: " + item.radiusMeters + "m");
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteClickListener != null) {
                deleteClickListener.onDeleteClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return locations != null ? locations.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCoords, tvRadius;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvCoords = itemView.findViewById(R.id.tvCoords);
            tvRadius = itemView.findViewById(R.id.tvRadius);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
