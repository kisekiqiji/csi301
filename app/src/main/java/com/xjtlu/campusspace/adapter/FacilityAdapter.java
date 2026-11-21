package com.xjtlu.campusspace.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xjtlu.campusspace.R;
import java.util.ArrayList;
import java.util.List;

/** A display row can represent equipment or a canonical public space without duplicating stored entities. */
public class FacilityAdapter extends RecyclerView.Adapter<FacilityAdapter.VH> {
    public static final class Item {
        public final String title, location, spaceId, equipmentId;
        public Item(String title, String location, String spaceId, String equipmentId) {
            this.title = title; this.location = location; this.spaceId = spaceId; this.equipmentId = equipmentId;
        }
    }
    public interface OnLink { void onLink(Item item); }
    private final List<Item> items = new ArrayList<>();
    private final OnLink onLink;
    public FacilityAdapter(OnLink listener) { onLink = listener; }
    public void setItems(List<Item> rows) {
        items.clear(); if (rows != null) items.addAll(rows); notifyDataSetChanged();
    }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_facility, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull VH holder, int position) {
        Item item = items.get(position);
        holder.type.setText(item.title); holder.loc.setText(item.location);
        holder.link.setVisibility(View.VISIBLE);
        holder.link.setText(item.equipmentId == null ? "查看空间详情 >" : "纸条 / 报修 >");
        holder.link.setOnClickListener(v -> onLink.onLink(item));
        holder.itemView.setOnClickListener(v -> onLink.onLink(item));
    }
    @Override public int getItemCount() { return items.size(); }
    static class VH extends RecyclerView.ViewHolder {
        final TextView type, loc, link;
        VH(@NonNull View view) {
            super(view);
            type = view.findViewById(R.id.tv_type);
            loc = view.findViewById(R.id.tv_loc);
            link = view.findViewById(R.id.tv_link);
        }
    }
}
