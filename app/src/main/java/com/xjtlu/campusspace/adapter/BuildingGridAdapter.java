package com.xjtlu.campusspace.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.data.entity.Building;
import java.util.ArrayList;
import java.util.List;

/** Full-width campus headings with selectable, illustrated building cards. */
public final class BuildingGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface OnSelect { void select(Building building); }
    public static final class Entry {
        public final String heading;
        public final Building building;
        public final int classrooms, publicSpaces;
        public Entry(String heading) { this.heading = heading; building = null; classrooms = publicSpaces = 0; }
        public Entry(Building building, int classrooms, int publicSpaces) {
            heading = null; this.building = building; this.classrooms = classrooms; this.publicSpaces = publicSpaces;
        }
    }
    private final List<Entry> entries = new ArrayList<>();
    private final OnSelect onSelect;
    private String selectedCode;
    private boolean publicScope;
    public BuildingGridAdapter(OnSelect onSelect) { this.onSelect = onSelect; }
    public void setItems(List<Entry> items, String selectedCode, boolean publicScope) {
        entries.clear(); entries.addAll(items); this.selectedCode = selectedCode; this.publicScope = publicScope;
        notifyDataSetChanged();
    }
    public int spanAt(int position) { return entries.get(position).heading != null ? 2 : 1; }
    @Override public int getItemCount() { return entries.size(); }
    @Override public int getItemViewType(int position) { return entries.get(position).heading != null ? 0 : 1; }
    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        View view = LayoutInflater.from(parent.getContext()).inflate(type == 0 ? R.layout.item_campus_heading : R.layout.item_building_card, parent, false);
        return type == 0 ? new Heading(view) : new Card(view);
    }
    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Entry entry = entries.get(position);
        if (holder instanceof Heading) { ((Heading) holder).label.setText(entry.heading); return; }
        Card card = (Card) holder;
        Building building = entry.building;
        card.code.setText(building.code);
        card.code.setTextSize(building.code.length() > 8 ? 16 : 27);
        card.name.setText(building.name.equals(building.code) ? building.campus : building.name);
        card.counts.setText(entry.classrooms + " 间教室 · " + entry.publicSpaces + " 处公共空间");
        boolean empty = (publicScope ? entry.publicSpaces : entry.classrooms) == 0;
        card.hint.setText(empty ? "暂无" + (publicScope ? "公共空间" : "教室") + "数据 · 可选择" : "点选查看  →");
        boolean selected = building.code.equals(selectedCode);
        card.card.setStrokeColor(Color.parseColor(selected ? "#526F46" : "#D9DED4"));
        card.card.setStrokeWidth(Math.round((selected ? 2 : 1) * card.itemView.getResources().getDisplayMetrics().density));
        card.card.setCardBackgroundColor(Color.parseColor(selected ? "#EDF2E5" : "#FFFEFA"));
        card.itemView.setContentDescription(building.campus + "，" + building.code + "，" + card.counts.getText() + "，" + card.hint.getText());
        card.itemView.setOnClickListener(v -> onSelect.select(building));
    }
    private static final class Heading extends RecyclerView.ViewHolder {
        final TextView label;
        Heading(View view) { super(view); label = (TextView) view; }
    }
    private static final class Card extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView code, name, counts, hint;
        Card(View view) {
            super(view); card = (MaterialCardView) view;
            code = view.findViewById(R.id.tv_building_code); name = view.findViewById(R.id.tv_building_name);
            counts = view.findViewById(R.id.tv_building_counts); hint = view.findViewById(R.id.tv_building_hint);
        }
    }
}
