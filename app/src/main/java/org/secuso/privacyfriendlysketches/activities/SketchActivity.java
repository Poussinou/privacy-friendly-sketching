/*
 This file is part of Privacy Friendly Sketching.

 Privacy Friendly Sketching is free software:
 you can redistribute it and/or modify it under the terms of the
 GNU General Public License as published by the Free Software Foundation,
 either version 3 of the License, or any later version.

 Privacy Friendly Sketching is distributed in the hope
 that it will be useful, but WITHOUT ANY WARRANTY; without even
 the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Privacy Friendly Sketching. If not, see <http://www.gnu.org/licenses/>.
 */
package org.secuso.privacyfriendlysketches.activities;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ShareActionProvider;
import android.widget.Toast;

import com.divyanshu.draw.widget.DrawView;
import com.divyanshu.draw.widget.MyPath;
import com.divyanshu.draw.widget.PaintOptions;
import com.divyanshu.draw.widget.CircleView;

import org.secuso.privacyfriendlysketches.R;
import org.secuso.privacyfriendlysketches.activities.helper.BaseActivity;
import org.secuso.privacyfriendlysketches.database.Sketch;
import org.secuso.privacyfriendlysketches.helpers.Utility;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

enum ToolbarMode {
    None,
    Width,
    Color,
    Opacity
}

/**
 * This class represents the editing of a sketch and gives the user the ability to draw sketches with different tools, rename sketches, select a
 * new background and export the sketches
 */
public class SketchActivity extends BaseActivity {
    static final int NEW_SKETCH_ID = -1;
    static final int TEMP_SKETCH_ID = -2;

    static final int IMAGE_RESULT_CODE = 1;
    static final int WRITE_PERMISSION_CODE = 2;
    private final static int SAVETYPE_GALLERY = 3;
    private final static int SAVETYPE_EXTERNAL = 4;

    private boolean toolbarOpen = false;
    private ToolbarMode toolbarMode = ToolbarMode.None;
    private DrawView drawView;
    private View toolbar;
    private CircleView preview;

    private Sketch sketch = null;
    private int focusedColor = 0;

    private View colorPalette;
    private SeekBar seekBarWidth;
    private SeekBar seekBarOpacity;

    private AlertDialog backgroundColorSelectDialog = null;
    private boolean writePermissionGranted = false;

    private static int SAVETYPE;

    private void initFromSketch(Sketch sketch) {
        this.sketch = sketch;
        LinkedHashMap<MyPath, PaintOptions> path = sketch.getPaths();
        if (path != null)
            for (Map.Entry<MyPath, PaintOptions> itr : path.entrySet())
                drawView.addPath(itr.getKey(), itr.getValue());
        drawView.setBackground(sketch.getBitmap());
    }

    private void updateSketchBeforeSave() {
        String description;
        int sketchId;

        if (sketch == null) {
            description = DateFormat.getDateInstance().format(new Date());
            sketchId = NEW_SKETCH_ID;
        } else {
            description = sketch.description;
            sketchId = sketch.id;
        }

        this.sketch = new Sketch(drawView.getPaintBackground(), drawView.getMPaths(), description);
        sketch.setId(sketchId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sketch);

        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);

        drawView = findViewById(R.id.draw_view);
        toolbar = findViewById(R.id.draw_tools);

        preview = findViewById(R.id.circle_view_preview);

        colorPalette = findViewById(R.id.draw_color_palette);
        seekBarWidth = findViewById(R.id.seekBar_width);
        seekBarOpacity = findViewById(R.id.seekBar_opacity);

        seekBarWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                drawView.setStrokeWidth((float) progress);
                preview.setCircleRadius((float) progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        seekBarOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                drawView.setAlpha(progress);
                preview.setAlpha(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        if (savedInstanceState != null) {
            int sketchId = savedInstanceState.getInt("sketchId");
            Sketch sketch = getRoomHandler().getSketch(TEMP_SKETCH_ID);
            if (sketch != null) {
                sketch.setId(sketchId);
                initFromSketch(sketch);
            }
        }

        if (sketch == null) {
            Bundle b = getIntent().getExtras();
            if (b != null) {
                int sketchId = b.getInt("sketchId", NEW_SKETCH_ID);
                if (sketchId != NEW_SKETCH_ID) {
                    Sketch sketch = getRoomHandler().getSketch(sketchId);
                    initFromSketch(sketch);
                }
            }
        }

        if (sketch == null) {
            String description = DateFormat.getDateInstance().format(new Date());
            sketch = new Sketch(null, drawView.getMPaths(), description);
            sketch.setId(NEW_SKETCH_ID);
        }

        ImageView iv = findViewById(R.id.image_close_drawing);
        iv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("SKETCH ACTIVITY", "top left menu clicked");
                final AlertDialog.Builder builder = new AlertDialog.Builder(SketchActivity.this);
                builder.setTitle(R.string.what_would_you_like_to_do)
                        .setItems(new String[]{
                                getResources().getString(R.string.select_background),
                                getResources().getString(R.string.rename_sketch),
                                getResources().getString(R.string.export_sketch)
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int which) {
                                Log.i("SKETCH ACTIVITY", "button " + which + " clicked");
                                dialogInterface.dismiss();
                                switch (which) {
                                    case 0: //select background
                                        Log.i("SKETCH ACTIVITY", "renaming..");
                                        final AlertDialog.Builder backgroundBuilder = new AlertDialog.Builder(SketchActivity.this);
                                        backgroundBuilder.setTitle(R.string.select_background);

                                        //create a dialog to select bgColor or bgImage
                                        LinearLayout l = new LinearLayout(SketchActivity.this);
                                        l.setOrientation(LinearLayout.VERTICAL);

                                        View backgroundPalette = LayoutInflater.from(SketchActivity.this).inflate(R.layout.select_background_menu, null);
//
                                        backgroundBuilder.setView(backgroundPalette);
                                        final AlertDialog dialog = backgroundBuilder.create();
                                        SketchActivity.this.backgroundColorSelectDialog = dialog;
                                        dialog.show();
                                        break;
                                    case 1: //rename sketch
                                        Log.i("SKETCH ACTIVITY", "renaming..");
                                        AlertDialog.Builder renameBuilder = new AlertDialog.Builder(SketchActivity.this);
                                        renameBuilder.setTitle(R.string.rename_sketch);

                                        final EditText input = new EditText(SketchActivity.this);

                                        if (sketch != null && sketch.description != null) {
                                            input.setText(sketch.description);
                                        }
                                        input.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER);
                                        input.setFilters(new InputFilter[]{new InputFilter() {
                                            @Override
                                            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                                                for (int i = start; i < end; i++) {
                                                    if (!Character.isLetterOrDigit(source.charAt(i))) {
                                                        return "";
                                                    }
                                                }
                                                return null;
                                            }
                                        }, new InputFilter.LengthFilter(30)});
                                        renameBuilder.setView(input);

                                        renameBuilder.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialogInterface, int i) {
                                                String newName = input.getText().toString();
                                                sketch.description = newName;
                                            }
                                        });
                                        renameBuilder.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialogInterface, int i) {
                                                return;
                                            }
                                        });

                                        renameBuilder.create();
                                        renameBuilder.show();
                                        break;
                                    case 2: //export sketch
                                        AlertDialog.Builder saveDialogBuilder = new AlertDialog.Builder(SketchActivity.this);
                                        saveDialogBuilder.setTitle(R.string.export_where);
                                        saveDialogBuilder.setItems(new String[]{
                                                getResources().getString(R.string.export_into_gallery),
                                                getResources().getString(R.string.export_into_external)
                                        }, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialogInterface, int i) {
                                                dialogInterface.dismiss();
                                                switch (i) {
                                                    case 0: //export to gallery
                                                        if (ContextCompat.checkSelfPermission(SketchActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                                            setSAVETYPE(SAVETYPE_GALLERY);
                                                            ActivityCompat.requestPermissions(SketchActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_PERMISSION_CODE);
                                                        } else {
                                                            setWritePermissionGranted(true);
                                                        }
                                                        if (writePermissionGranted) {
                                                            saveSketchIntoGallery();
                                                        }
                                                        break;
                                                    case 1: //export to external storage
                                                        if (ContextCompat.checkSelfPermission(SketchActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                                            setSAVETYPE(SAVETYPE_EXTERNAL);
                                                            ActivityCompat.requestPermissions(SketchActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_PERMISSION_CODE);
                                                        } else {
                                                            setWritePermissionGranted(true);
                                                        }
                                                        if (writePermissionGranted) {
                                                            saveSketchIntoExternal();
                                                        }
                                                        break;
                                                }
                                                return;
                                            }


                                        });
                                        AlertDialog saveDialog = saveDialogBuilder.create();
                                        saveDialog.show();

                                        break;
                                    default:
                                        return;
                                }

                            }
                        });
                AlertDialog dialog = builder.create();
                dialog.show();

            }
        });

    }

    @Override
    protected void onPause() {
        super.onPause();

        if (drawView.getMPaths().size() == 0)
            return;

        updateSketchBeforeSave();
        if (sketch.id != NEW_SKETCH_ID)
            getRoomHandler().updateSketch(sketch);
        else {
            sketch.id = 0; // use auto increment
            sketch.id = getRoomHandler().insertSketch(sketch);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (drawView.getMPaths().size() == 0)
            return;

        updateSketchBeforeSave();
        outState.putInt("sketchId", sketch.id);
        sketch.setId(TEMP_SKETCH_ID);
        getRoomHandler().insertSketch(sketch);
    }


    @Override
    protected int getNavigationDrawerID() {
        return R.id.nav_sketch;
    }

    public void onShare(View view) {
        if (ContextCompat.checkSelfPermission(SketchActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(SketchActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_PERMISSION_CODE);
            return;
        }

        String description = DateFormat.getDateInstance().format(new Date());
        if (sketch != null)
            description = sketch.description;
        String bitmapPath = MediaStore.Images.Media.insertImage(getContentResolver(), drawView.getBitmap(), description, null);
        Uri bitmapUri = Uri.parse(bitmapPath);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, bitmapUri);
        startActivity(intent);
    }

    public void onClick(View view) {
        ToolbarMode toolbarMode = ToolbarMode.None;

        switch (view.getId()) {
            case R.id.image_draw_eraser:
                clearCanvasDialogue();
                break;
            case R.id.image_draw_width:
                toolbarMode = ToolbarMode.Width;
                break;
            case R.id.image_draw_opacity:
                toolbarMode = ToolbarMode.Opacity;
                break;
            case R.id.image_draw_color:
                toolbarMode = ToolbarMode.Color;
                break;
            case R.id.image_draw_undo:
                drawView.undo();
                break;
            case R.id.image_draw_redo:
                drawView.redo();
                break;
            default:
                break;
        }

        if (toolbarMode != ToolbarMode.None) {
            if (toolbarMode == this.toolbarMode || !toolbarOpen)
                toggleToolbar();

            setToolbarMode(toolbarMode);
        }
    }

    private void clearCanvasDialogue() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.dialog_delete_message)
                .setMessage(R.string.dialog_delete_message);

        builder.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int id) {
                drawView.clearCanvas();
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void onSelectBackgroundImage(View v) {
        Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, IMAGE_RESULT_CODE);
        if (this.backgroundColorSelectDialog != null) {
            this.backgroundColorSelectDialog.dismiss();
        }
    }

    public void onSelectBackgroundColor(View v) {
        int colorId = 0;
        switch (v.getId()) {
            case R.id.image_color_black:
                colorId = R.color.color_black;
                break;
            case R.id.image_color_blue:
                colorId = R.color.color_blue;
                break;
            case R.id.image_color_brown:
                colorId = R.color.color_brown;
                break;
            case R.id.image_color_green:
                colorId = R.color.color_green;
                break;
            case R.id.image_color_pink:
                colorId = R.color.color_pink;
                break;
            case R.id.image_color_red:
                colorId = R.color.color_red;
                break;
            case R.id.image_color_yellow:
                colorId = R.color.color_yellow;
                break;
        }

        if (colorId != 0) {
            Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            b.eraseColor(getResources().getColor(colorId));
            drawView.setBackground(b);
            if (this.backgroundColorSelectDialog != null) {
                this.backgroundColorSelectDialog.dismiss();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMAGE_RESULT_CODE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap bmp = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                this.drawView.setBackground(bmp);
            } catch (IOException e) {
                Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void onSelectColor(View view) {
        int colorId = 0;
        int toFocusedColor = view.getId();

        switch (view.getId()) {
            case R.id.image_color_black:
                colorId = R.color.color_black;
                break;
            case R.id.image_color_blue:
                colorId = R.color.color_blue;
                break;
            case R.id.image_color_brown:
                colorId = R.color.color_brown;
                break;
            case R.id.image_color_green:
                colorId = R.color.color_green;
                break;
            case R.id.image_color_pink:
                colorId = R.color.color_pink;
                break;
            case R.id.image_color_red:
                colorId = R.color.color_red;
                break;
            case R.id.image_color_yellow:
                colorId = R.color.color_yellow;
                break;
        }

        if (colorId != 0) {
            changeColorFocus(this.focusedColor, toFocusedColor);
            this.focusedColor = toFocusedColor;
            drawView.setColor(getResources().getColor(colorId));
            preview.setColor(getResources().getColor(colorId));
        }
    }

    private void changeColorFocus(int fromColorId, int toColorId) {
        if (fromColorId == 0) {
            fromColorId = R.id.image_color_black;
        }
        ImageView fromView = findViewById(fromColorId);
        fromView.setScaleX(1f);
        fromView.setScaleY(1f);

        ImageView toView = findViewById(toColorId);
        toView.setScaleY(1.5f);
        toView.setScaleX(1.5f);
    }

    private void setToolbarMode(ToolbarMode mode) {
        if (this.toolbarMode == mode)
            return;

        colorPalette.setVisibility(View.GONE);
        seekBarWidth.setVisibility(View.GONE);
        seekBarOpacity.setVisibility(View.GONE);

        switch (mode) {
            case Color:
                colorPalette.setVisibility(View.VISIBLE);
                break;
            case Width:
                seekBarWidth.setVisibility(View.VISIBLE);
                break;
            case Opacity:
                seekBarOpacity.setVisibility(View.VISIBLE);
                break;
            default:
                throw new IllegalArgumentException();
        }

        this.toolbarMode = mode;
    }

    private void toggleToolbar() {
        toolbar.animate().translationY((toolbarOpen ? 56 : 0) * getResources().getDisplayMetrics().density);
        toolbarOpen = !toolbarOpen;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case WRITE_PERMISSION_CODE:
                switch (this.SAVETYPE) {
                    case SAVETYPE_GALLERY:
                        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                            this.writePermissionGranted = true;
                            saveSketchIntoGallery();
                        } else if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                            Toast.makeText(SketchActivity.this, R.string.permission_error, Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case SAVETYPE_EXTERNAL:
                        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                            this.writePermissionGranted = true;
                            saveSketchIntoExternal();
                        } else if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                            Toast.makeText(SketchActivity.this, R.string.permission_error, Toast.LENGTH_SHORT).show();
                        }
                        break;
                }

                break;
        }
    }

    public void setWritePermissionGranted(boolean b) {
        this.writePermissionGranted = b;
    }

    public void setSAVETYPE(int i) {
        this.SAVETYPE = i;
    }

    public void saveSketchIntoGallery() {
        Bitmap bmp = drawView.getBitmap();
        updateSketchBeforeSave();
        MediaStore.Images.Media.insertImage(getContentResolver(), bmp, sketch.getDescription(), null);
        Toast.makeText(SketchActivity.this, R.string.sketch_saved, Toast.LENGTH_SHORT).show();
    }

    public void saveSketchIntoExternal() {
        if (Utility.isExternalStorageWritable()) {
            String root = Environment.getExternalStorageDirectory().toString();
            OutputStream os;
            File dir = new File(root + "/Sketches");
            dir.mkdirs();
            File f = new File(dir, sketch.getDescription() + ".jpg");
            Bitmap bmp = drawView.getBitmap();
            if (f.exists()) {
                f.delete();
            }

            try {
                os = new FileOutputStream(f);
                bmp.compress(Bitmap.CompressFormat.JPEG, 100, os);
                os.flush();
                os.close();

                MediaStore.Images.Media.insertImage(getContentResolver(), f.getAbsolutePath(), f.getName(), f.getName());
                Toast.makeText(SketchActivity.this, R.string.sketch_saved, Toast.LENGTH_SHORT).show();
            } catch (FileNotFoundException e) {
                Toast.makeText(SketchActivity.this, "FILE NOT FOUND ERROR", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(SketchActivity.this, "IO EXCEPTION", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.i("SKETCH_ACTIVITY", "external NOT writable");
        }
    }

}

