package com.xjtlu.campusspace.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.data.entity.Facility;

import java.util.ArrayList;
import java.util.List;

/** 设备位置列表 */
public class FacilityAdapter extends RecyclerView.Adapter<FacilityAdapter.VH> {

    public interface OnLink {
        void onLink(Facility facility);
    }

    private final List<Facility> items = new ArrayList<>();
    private final OnLink onLink;

    public FacilityAdapter(OnLink onLink) {
        this.onLink = onLink;
    }

    public void setItems(List<Facility> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_facility, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Facility f = items.get(position);
        h.type.setText(f.type);
        h.loc.setText(f.locationText);

        boolean linkable = "休息处".equals(f.type) || "开放座位区".equals(f.type);
        h.link.setVisibility(linkable ? View.VISIBLE : View.GONE);
        h.link.setOnClickListener(v -> {
            if (onLink != null) onLink.onLink(f);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView type;
        final TextView loc;
        final TextView link;

        VH(@NonNull View v) {
            super(v);
            type = v.findViewById(R.id.tv_type);
            loc = v.findViewById(R.id.tv_loc);
            link = v.findViewById(R.id.tv_link);
        }
    }
}
