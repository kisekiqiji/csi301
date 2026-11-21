package com.xjtlu.campusspace.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.core.StatusCalculator;
import com.xjtlu.campusspace.data.entity.Space;
import com.xjtlu.campusspace.ui.widget.Pills;

import java.util.ArrayList;
import java.util.List;

/** 首页/详情通用的空间列表 */
public class SpaceAdapter extends RecyclerView.Adapter<SpaceAdapter.VH> {

    public interface OnClick {
        void onClick(Space space);
    }

    /** 一行数据：空间 + 已算好的状态 + 副标题 */
    public static class Item {
        public final Space space;
        public final StatusCalculator.State state;
        public final String subtitle;

        public Item(Space space, StatusCalculator.State state, String subtitle) {
            this.space = space;
            this.state = state;
            this.subtitle = subtitle;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final OnClick onClick;

    public SpaceAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    public void setItems(List<Item> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_space, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item it = items.get(position);
        Space s = it.space;

        h.name.setText(s.name);
        h.sub.setText(it.subtitle);

        String fac = s.facilities == null ? "" : s.facilities.replace(",", " · ");
        h.fac.setText(fac);
        h.fac.setVisibility(fac.isEmpty() ? View.GONE : View.VISIBLE);

        int color;
        String label;
        if (it.state == StatusCalculator.State.IN_CLASS) {
            color = h.itemView.getContext().getResources().getColor(R.color.state_busy, null);
            label = "CLASSROOM".equals(s.type) || s.isDiscussionArea() ? "上课中" : "暂无空余";
        } else if (it.state == StatusCalculator.State.SOON) {
            color = h.itemView.getContext().getResources().getColor(R.color.state_soon, null);
            label = "即将上课";
        } else if (it.state == StatusCalculator.State.FREE) {
            color = h.itemView.getContext().getResources().getColor(R.color.state_free, null);
            label = "CLASSROOM".equals(s.type) || s.isDiscussionArea() ? "空闲中" : "有空余";
        } else {
            color = 0xFF7D8592;
            label = "状态未知";
        }
        Pills.apply(h.state, color);
        h.state.setText(label);

        h.itemView.setOnClickListener(v -> {
            if (onClick != null) onClick.onClick(s);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView state;
        final TextView sub;
        final TextView fac;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.tv_name);
            state = v.findViewById(R.id.tv_state);
            sub = v.findViewById(R.id.tv_sub);
            fac = v.findViewById(R.id.tv_fac);
        }
    }
}
