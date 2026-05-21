package com.example.ruker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

public class PathAdapter extends RecyclerView.Adapter<PathAdapter.PathViewHolder> {

    public interface OnPathClickListener {
        void onPathClick(PathItem path);
    }

    public interface OnVisibilityChanged {
        void onVisibilityChanged(PathItem path, boolean isPublic);
    }

    private final List<PathItem> paths;
    private final OnPathClickListener clickListener;
    private final OnVisibilityChanged visibilityListener;

    public PathAdapter(List<PathItem> paths,
                       OnPathClickListener clickListener,
                       OnVisibilityChanged visibilityListener) {
        this.paths = paths;
        this.clickListener = clickListener;
        this.visibilityListener = visibilityListener;
    }

    @NonNull
    @Override
    public PathViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.activity_item_path, parent, false);
        return new PathViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PathViewHolder holder, int position) {
        PathItem path = paths.get(position);

        holder.pathName.setText(path.displayName);
        holder.publicLabel.setText(path.isPublic ? "Public" : "Private");

        holder.publicSwitch.setOnCheckedChangeListener(null);
        holder.publicSwitch.setChecked(path.isPublic);

        holder.pathInfoLayout.setOnClickListener(v ->
                clickListener.onPathClick(path)
        );

        holder.publicSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            path.isPublic = isChecked;
            holder.publicLabel.setText(isChecked ? "Public" : "Private");
            visibilityListener.onVisibilityChanged(path, isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return paths.size();
    }

    static class PathViewHolder extends RecyclerView.ViewHolder {
        View pathInfoLayout;
        TextView pathName;
        TextView publicLabel;
        SwitchMaterial publicSwitch;

        PathViewHolder(View itemView) {
            super(itemView);
            pathInfoLayout  = itemView.findViewById(R.id.pathInfoLayout);
            pathName        = itemView.findViewById(R.id.pathName);
            publicLabel     = itemView.findViewById(R.id.publicLabel);
            publicSwitch    = itemView.findViewById(R.id.publicSwitch);
        }
    }
}
