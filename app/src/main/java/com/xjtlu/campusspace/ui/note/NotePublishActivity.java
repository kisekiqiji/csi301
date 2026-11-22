package com.xjtlu.campusspace.ui.note;

import android.graphics.Bitmap;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.core.TimeProvider;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.LocalAuth;
import com.xjtlu.campusspace.ui.account.AccountActivity;
import com.xjtlu.campusspace.data.entity.Building;
import com.xjtlu.campusspace.data.entity.Facility;
import com.xjtlu.campusspace.data.entity.Note;
import com.xjtlu.campusspace.data.entity.Space;
import com.xjtlu.campusspace.data.store.Rows;
import com.xjtlu.campusspace.util.ImageStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Every note must bind to an existing concrete space before it can be saved. */
public class NotePublishActivity extends AppCompatActivity {
    public static final String EXTRA_SPACE_ID = "space_id";
    public static final String EXTRA_EQUIPMENT_ID = "equipment_id";
    public static final String EXTRA_TARGET_TYPE = "target_type";
    public static final String EXTRA_TARGET_ID = "target_id";
    public static final String EXTRA_TARGET_NAME = "target_name";
    private Repo repo;
    private String spaceId, equipmentId, draftId;
    private final ArrayList<String> imagePaths = new ArrayList<>();
    private final ExecutorService imagesIo = Executors.newSingleThreadExecutor();
    private EditText content;
    private ImageView preview;
    private TextView addImage, imageCount;
    private Chip location;
    private boolean publishing, copying, ready, prompted;
    private PublishState publishState;
    private boolean accountPending, freshDraft;
    private final ActivityResultLauncher<Intent> account = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                accountPending = false;
                if (LocalAuth.get(this).currentUser() == null) { finish(); return; }
                loadSpace();
            });

    /** Retain an in-flight write across rotation without retaining the Activity. */
    public static class PublishState extends ViewModel {
        final MutableLiveData<String> completion = new MutableLiveData<>();
        boolean running;
        void publish(Repo repo, Note note) {
            if (running) return;
            running = true;
            repo.addNote(note, () -> { running = false; completion.setValue(""); },
                    error -> { running = false; completion.setValue(error == null ? "请重试" : error); });
        }
    }
    private final ActivityResultLauncher<PickVisualMediaRequest> picker =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(4), uris -> {
                if (uris.isEmpty() || publishing) return;
                copying = true; updatePublishButton();
                imagesIo.execute(() -> {
                    ArrayList<String> paths = new ArrayList<>();
                    for (android.net.Uri uri : uris) {
                        String path = ImageStore.copyToPrivate(this, uri);
                        if (path != null) paths.add(path);
                    }
                    Bitmap thumb = paths.isEmpty() ? null : ImageStore.loadThumb(paths.get(0), 600);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        copying = false;
                        if (paths.size() != uris.size()) {
                            toast("部分图片保存失败，请重新选择"); updatePublishButton(); return;
                        }
                        imagePaths.clear(); imagePaths.addAll(paths);
                        showImages(thumb); updatePublishButton();
                    });
                });
            });

    @Override protected void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved); setContentView(R.layout.activity_note_publish);
        accountPending = saved != null && saved.getBoolean("account_pending");
        freshDraft = saved == null || saved.getBoolean("fresh_draft");
        repo = Repo.get(this);
        content = findViewById(R.id.et_content);
        preview = findViewById(R.id.iv_preview);
        addImage = findViewById(R.id.tv_add_image);
        imageCount = findViewById(R.id.tv_image_count);
        location = findViewById(R.id.chip_location);
        publishState = new ViewModelProvider(this).get(PublishState.class);
        publishing = publishState.running;
        publishState.completion.observe(this, result -> {
            if (result == null) return;
            publishState.completion.setValue(null);
            publishing = false;
            if (result.isEmpty()) { toast("发布成功"); setResult(RESULT_OK); finish(); }
            else { updatePublishButton(); toast("发布失败：" + result); }
        });
        ((MaterialToolbar)findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> {
            if (!publishing) finish();
        });
        spaceId = saved == null ? getIntent().getStringExtra(EXTRA_SPACE_ID) : saved.getString("space_id");
        equipmentId = saved == null ? getIntent().getStringExtra(EXTRA_EQUIPMENT_ID) : saved.getString("equipment_id");
        draftId = saved == null ? Repo.newNoteId() : saved.getString("draft_id", Repo.newNoteId());
        prompted = saved != null && saved.getBoolean("prompted");
        if (saved != null) {
            ArrayList<String> paths = saved.getStringArrayList("images");
            if (paths != null) imagePaths.addAll(paths);
        }
        location.setText("#请选择具体空间");
        location.setOnClickListener(v -> { if (ready && !publishing) chooseBuilding(); });
        findViewById(R.id.box_image).setOnClickListener(v -> {
            if (!publishing && !copying) picker.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
        });
        imageCount.setOnClickListener(v -> {
            if (publishing || copying || imagePaths.isEmpty()) return;
            new AlertDialog.Builder(this).setItems(new String[]{"移除全部图片"}, (d, n) -> {
                imagePaths.clear(); showImages(null);
            }).show();
        });
        findViewById(R.id.btn_publish).setOnClickListener(v -> publish());
        updatePublishButton();
        if (!imagePaths.isEmpty()) showImages(ImageStore.loadThumb(imagePaths.get(0), 600));
        ensureAccount();
    }

    private void ensureAccount() {
        LocalAuth.get(this).ensureLoaded(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (LocalAuth.get(this).currentUser() != null) { loadSpace(); return; }
            if (!accountPending) {
                accountPending = true;
                toast("登录本机账号后即可发布纸条");
                account.launch(new Intent(this, AccountActivity.class));
            }
        });
    }

    private void loadSpace() {
        repo.ensureLoaded(() -> {
            if (isFinishing() || isDestroyed()) return;
            ready = repo.isLoaded();
            if (!ready) { toast("空间数据加载失败，请返回后重试"); updatePublishButton(); return; }
            if (spaceId == null) {
                Space legacy = repo.spaceForTarget(getIntent().getStringExtra(EXTRA_TARGET_TYPE),
                        getIntent().getStringExtra(EXTRA_TARGET_ID));
                if (legacy != null) spaceId = legacy.id;
            }
            if (spaceId != null && repo.spaceOf(spaceId) == null) {
                toast("原位置已不存在，请重新选择空间"); spaceId = null; equipmentId = null;
            }
            if (equipmentId != null) {
                Facility equipment = repo.facilityOf(equipmentId);
                if (equipment == null || !equipment.spaceId.equals(spaceId)) {
                    toast("设备位置无效，请重新选择"); equipmentId = null;
                } else if (freshDraft) ((RadioGroup)findViewById(R.id.group_category)).check(R.id.rb_repair);
            }
            freshDraft = false;
            showLocation(); updatePublishButton();
            if (spaceId == null && !prompted) { prompted = true; chooseBuilding(); }
        });
    }

    private void chooseBuilding() {
        List<Building> buildings = repo.buildings;
        String[] labels = new String[buildings.size()];
        for (int n = 0; n < labels.length; n++) labels[n] = buildings.get(n).campus + " · " + buildings.get(n).code;
        new AlertDialog.Builder(this).setTitle("选择位置 · 楼栋").setItems(labels,
                (dialog, which) -> chooseFloor(buildings.get(which).code)).setNegativeButton("取消", null).show();
    }
    private void chooseFloor(String building) {
        List<Rows.Floor> floors = repo.floorsOf(building);
        String[] labels = new String[floors.size()];
        for (int n = 0; n < labels.length; n++) labels[n] = floors.get(n).code;
        if (labels.length == 0) { toast("这个楼栋暂无楼层数据"); return; }
        new AlertDialog.Builder(this).setTitle("选择位置 · " + building).setItems(labels,
                (dialog, which) -> chooseSpace(building, floors.get(which).code)).setNegativeButton("取消", null).show();
    }
    private void chooseSpace(String building, String floor) {
        List<Space> choices = new ArrayList<>();
        for (Space s : repo.allSpaces()) {
            if (building.equals(s.building) && floor.equals(s.floorCode) && !"PUBLIC_AREA".equals(s.type)) choices.add(s);
        }
        if (choices.isEmpty()) { toast("这一层暂无可选空间"); return; }
        String[] labels = new String[choices.size()];
        for (int n = 0; n < labels.length; n++) labels[n] = choices.get(n).name;
        new AlertDialog.Builder(this).setTitle("选择具体空间 · " + building + " " + floor).setItems(labels, (dialog, which) -> {
            spaceId = choices.get(which).id; equipmentId = null; showLocation(); updatePublishButton();
        }).setNegativeButton("取消", null).show();
    }
    private void showLocation() {
        Space space = repo.spaceOf(spaceId);
        Facility equipment = equipmentId == null ? null : repo.facilityOf(equipmentId);
        location.setText(space == null ? "#请选择具体空间" : "#" + space.name
                + (equipment == null ? "" : " · " + equipment.type + " " + equipment.locationText));
    }
    private void showImages(Bitmap bitmap) {
        preview.setImageBitmap(bitmap);
        preview.setVisibility(bitmap == null ? View.GONE : View.VISIBLE);
        addImage.setVisibility(bitmap == null ? View.VISIBLE : View.GONE);
        imageCount.setText(imagePaths.isEmpty() ? "最多 4 张图片" : "已选 " + imagePaths.size() + " 张 · 点击图片更换 / 点此移除");
    }
    private void updatePublishButton() {
        TextView button = findViewById(R.id.btn_publish);
        button.setEnabled(ready && !publishing && !copying);
        button.setText(publishing ? "正在发布…" : copying ? "正在保存图片…" : "发布");
    }
    private void publish() {
        if (!ready || publishing || copying) return;
        if (LocalAuth.get(this).currentUser() == null) { ensureAccount(); return; }
        Space space = repo.spaceOf(spaceId);
        if (space == null) { toast("请先选择具体空间"); chooseBuilding(); return; }
        if (equipmentId != null) {
            Facility equipment = repo.facilityOf(equipmentId);
            if (equipment == null || !space.id.equals(equipment.spaceId)) {
                toast("设备不属于当前空间，请重新选择位置"); return;
            }
        }
        String body = content.getText() == null ? "" : content.getText().toString().trim();
        if (body.isEmpty()) { content.setError("请填写纸条正文"); content.requestFocus(); return; }
        if (body.length() > 2000) { content.setError("正文最多 2000 字"); content.requestFocus(); return; }
        int category = ((RadioGroup)findViewById(R.id.group_category)).getCheckedRadioButtonId();
        if (category != R.id.rb_lost && category != R.id.rb_chat && category != R.id.rb_repair) {
            toast("请选择纸条类型"); return;
        }
        Note note = new Note();
        note.id = draftId; note.spaceId = space.id; note.equipmentId = equipmentId;
        note.targetType = equipmentId == null ? Note.T_SPACE : Note.T_FACILITY;
        note.targetId = equipmentId == null ? space.id : equipmentId;
        note.category = category == R.id.rb_repair ? Note.C_REPAIR : category == R.id.rb_chat ? Note.C_CHAT : Note.C_LOST;
        note.content = body; note.imagePaths = new ArrayList<>(imagePaths);
        note.imagePath = imagePaths.isEmpty() ? null : imagePaths.get(0); note.createdAt = TimeProvider.now();
        publishing = true; updatePublishButton();
        publishState.publish(repo, note);
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        out.putString("space_id", spaceId); out.putString("equipment_id", equipmentId);
        out.putString("draft_id", draftId); out.putStringArrayList("images", imagePaths); out.putBoolean("prompted", prompted);
        out.putBoolean("account_pending", accountPending); out.putBoolean("fresh_draft", freshDraft);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() { imagesIo.shutdown(); super.onDestroy(); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
