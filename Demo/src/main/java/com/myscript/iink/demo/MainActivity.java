// Copyright MyScript. All rights reserved.

package com.myscript.iink.demo;

import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import com.myscript.iink.Configuration;
import com.myscript.iink.ContentPart;
import com.myscript.iink.Editor;
import com.myscript.iink.Engine;
import com.myscript.iink.IEditorListener;
import com.myscript.iink.uireferenceimplementation.EditorView;
import com.myscript.iink.uireferenceimplementation.FontUtils;
import com.myscript.iink.uireferenceimplementation.ImageLoader;
import com.myscript.iink.uireferenceimplementation.InputController;

import java.io.File;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";

    private static final String INPUT_MODE_KEY = "inputMode";

    protected Engine engine;

    protected EditorView editorView;

    protected DocumentController documentController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ErrorActivity.installHandler(this);

        engine = IInkApplication.getEngine();

        // configure recognition
        Configuration conf = engine.getConfiguration();
        String confDir = "zip://" + getPackageCodePath() + "!/assets/conf";
        conf.setStringArray("configuration-manager.search-path", new String[]{confDir});
        String tempDir = getFilesDir().getPath() + File.separator + "tmp";
        conf.setString("content-package.temp-folder", tempDir);

        setContentView(R.layout.activity_main);

        editorView = findViewById(R.id.editor_view);

        // load fonts
        AssetManager assetManager = getApplicationContext().getAssets();
        Map<String, Typeface> typefaceMap = FontUtils.loadFontsFromAssets(assetManager);
        editorView.setTypefaces(typefaceMap);

        editorView.setEngine(engine);

        final Editor editor = editorView.getEditor();
        // 暂时写死的theme
        editor.setTheme(".text {\n" +
                "  font-size: 30;\n" +
                "  line-height: 2.5;\n" +
                "}");
        editor.addListener(new IEditorListener() {
            @Override
            public void partChanging(Editor editor, ContentPart oldPart, ContentPart newPart) {
                // no-op
            }

            @Override
            public void partChanged(Editor editor) {
                invalidateOptionsMenu();
                invalidateIconButtons();
            }

            @Override
            public void contentChanged(Editor editor, String[] blockIds) {
                invalidateOptionsMenu();
                invalidateIconButtons();
            }

            @Override
            public void onError(Editor editor, String blockId, String message) {
                Log.e(TAG, "Failed to edit block \"" + blockId + "\"" + message);
            }
        });

        editorView.setImageLoader(new ImageLoader(editor, this.getCacheDir()));

        int inputMode = InputController.INPUT_MODE_FORCE_PEN; // If using an active pen, put INPUT_MODE_AUTO here
        if (savedInstanceState != null) {
            inputMode = savedInstanceState.getInt(INPUT_MODE_KEY, inputMode);
        }
        editorView.setInputMode(inputMode);

        documentController = new DocumentController(this, editorView);
        final String fileName = documentController.getSavedFileName();
        final int partIndex = documentController.getSavedPartIndex();

        // wait for view size initialization before setting part
        editorView.post(new Runnable() {
            @Override
            public void run() {
                if (fileName != null)
                    documentController.openPart(fileName, partIndex);
                else
                    documentController.newPart();
            }
        });

        findViewById(R.id.button_clear).setOnClickListener(this);

        invalidateIconButtons();
    }

    @Override
    protected void onDestroy() {
        editorView.setOnTouchListener(null);
        editorView.close();

        documentController.close();

        // IInkApplication has the ownership, do not close here
        engine = null;

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        documentController.saveToTemp();
        outState.putInt(INPUT_MODE_KEY, editorView.getInputMode());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_clear:
                editorView.getEditor().clear();
                break;
            default:
                Log.e(TAG, "Failed to handle click event");
                break;
        }
    }

    private void invalidateIconButtons() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ImageButton imageButtonClear = findViewById(R.id.button_clear);
                imageButtonClear.setEnabled(documentController != null && documentController.hasPart());
            }
        });
    }
}
