package com.xjtlu.campusspace.ui.note;

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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.core.TimeProvider;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.entity.Note;
import com.xjtlu.campusspace.util.ImageStore;

import java.util.Random;

/** 页面 6：纸条发布 */
public class NotePublishActivity extends AppCompatActivity {

    public static final String EXTRA_TARGET_TYPE = "target_type";
    public static final String EXTRA_TARGET_ID = "target_id";
    public static final String EXTRA_TARGET_NAME = "target_name";

    private static final String[] NICKNAMES = {"一只路过的", "期末人", "赶 due 中",
            "自习室幽灵", "咖啡续命", "匿名用户", "小透明", "卷不动了"};

    private String targetType = Note.T_SPACE;
    private String targetId = "";
    private String targetName = "";
    private String imagePath;

    private EditText etContent;
    private ImageView ivPreview;
    private TextView tvAddImage;

    // Photo Picker：不需要申请相册权限
    private final ActivityResultLauncher<PickVisualMediaRequest> picker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) return;
                String path = ImageStore.copyToPrivate(this, uri);
                if (path == null) {
                    Toast.makeText(this, "图片保存失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                imagePath = path;
                android.graphics.Bitmap bmp = ImageStore.loadThumb(path, 600);
                if (bmp != null) {
                    ivPreview.setImageBitmap(bmp);
                    ivPreview.setVisibility(View.VISIBLE);
                    tvAddImage.setVisibility(View.GONE);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_publish);

        targetType = getIntent().getStringExtra(EXTRA_TARGET_TYPE);
        targetId = getIntent().getStringExtra(EXTRA_TARGET_ID);
        targetName = getIntent().getStringExtra(EXTRA_TARGET_NAME);
        if (targetType == null) targetType = Note.T_SPACE;

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etContent = findViewById(R.id.et_content);
        ivPreview = findViewById(R.id.iv_preview);
        tvAddImage = findViewById(R.id.tv_add_image);

        Chip chip = findViewById(R.id.chip_location);
        chip.setText("#" + (targetName == null ? "校园" : targetName));

        findViewById(R.id.box_image).setOnClickListener(v -> picker.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        findViewById(R.id.btn_publish).setOnClickListener(v -> publish());
    }

    private void publish() {
        RadioGroup group = findViewById(R.id.group_category);
        String category;
        int checked = group.getCheckedRadioButtonId();
        if (checked == R.id.rb_repair) {
            category = Note.C_REPAIR;
        } else if (checked == R.id.rb_chat) {
            category = Note.C_CHAT;
        } else {
            category = Note.C_LOST;
        }

        String content = etContent.getText() == null ? "" : etContent.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "写点什么再发布吧", Toast.LENGTH_SHORT).show();
            return;
        }

        Note n = new Note();
        n.id = Repo.newNoteId();
        n.targetType = targetType;
        n.targetId = targetId == null ? "" : targetId;
        n.category = category;
        n.content = content;
        n.imagePath = imagePath;
        n.createdAt = TimeProvider.now();
        n.nickname = NICKNAMES[new Random().nextInt(NICKNAMES.length)];

        Repo.get(this).addNote(n, () -> {
            Toast.makeText(this, "发布成功", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
