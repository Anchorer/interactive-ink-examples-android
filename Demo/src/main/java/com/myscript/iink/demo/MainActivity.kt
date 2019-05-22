// Copyright MyScript. All rights reserved.

package com.myscript.iink.demo

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.ImageButton
import com.myscript.iink.ContentPart
import com.myscript.iink.Editor
import com.myscript.iink.Engine
import com.myscript.iink.IEditorListener
import com.myscript.iink.uireferenceimplementation.EditorView
import com.myscript.iink.uireferenceimplementation.FontUtils
import com.myscript.iink.uireferenceimplementation.ImageLoader
import com.myscript.iink.uireferenceimplementation.InputController
import java.io.File

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private var engine: Engine? = null

    private var editorView: EditorView? = null

    private var documentController: DocumentController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ErrorActivity.installHandler(this)

        engine = IInkApplication.getEngine()

        // configure recognition
        val conf = engine!!.configuration
        val confDir = "zip://$packageCodePath!/assets/conf"
        conf.setStringArray("configuration-manager.search-path", arrayOf(confDir))
        val tempDir = filesDir.path + File.separator + "tmp"
        conf.setString("content-package.temp-folder", tempDir)

        setContentView(R.layout.activity_main)

        editorView = findViewById(R.id.editor_view)

        // load fonts
        val assetManager = applicationContext.assets
        val typefaceMap = FontUtils.loadFontsFromAssets(assetManager)
        editorView?.setTypefaces(typefaceMap)

        editorView?.setEngine(engine!!)

        val editor = editorView?.editor
        // FIXME Temp theme
        editor!!.theme = ".text {\n" +
                "  font-size: 30;\n" +
                "  line-height: 2.5;\n" +
                "}"
        // Set width and height for the editorView
        // editor.setViewSize(0, 0);
        editor.addListener(object : IEditorListener {
            override fun partChanging(editor: Editor?, oldPart: ContentPart?, newPart: ContentPart?) {
                // no-op
            }

            override fun partChanged(editor: Editor?) {
                invalidateIconButtons()
            }

            override fun contentChanged(editor: Editor?, blockIds: Array<String>?) {
                invalidateIconButtons()
            }

            override fun onError(editor: Editor?, blockId: String?, message: String?) {
                Log.e(Consts.TAG, "Failed to edit block \"$blockId\"$message")
            }
        })

        editorView?.imageLoader = ImageLoader(editor, this.cacheDir)

        var inputMode =
            InputController.INPUT_MODE_FORCE_PEN // If using an active pen, put INPUT_MODE_AUTO here
        if (savedInstanceState != null) {
            inputMode = savedInstanceState.getInt(INPUT_MODE_KEY, inputMode)
        }
        editorView?.inputMode = inputMode

        documentController = DocumentController(this, editorView)
        val fileName = documentController!!.savedFileName
        val partIndex = documentController!!.savedPartIndex

        // wait for view size initialization before setting part
        editorView?.post {
            if (fileName != null) {
                documentController!!.openPart(fileName, partIndex)
            } else {
                documentController!!.newPart()
            }
        }

        findViewById<View>(R.id.button_clear).setOnClickListener(this)

        invalidateIconButtons()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onDestroy() {
        editorView?.setOnTouchListener(null)
        editorView?.close()

        documentController!!.close()

        // IInkApplication has the ownership, do not close here
        engine = null

        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        documentController!!.saveToTemp()
        outState.putInt(INPUT_MODE_KEY, editorView?.inputMode ?: InputController.INPUT_MODE_FORCE_PEN)
        super.onSaveInstanceState(outState)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.button_clear -> editorView?.editor!!.clear()
            else -> Log.e(Consts.TAG, "Failed to handle click event")
        }
    }

    private fun invalidateIconButtons() {
        runOnUiThread {
            val imageButtonClear = findViewById<ImageButton>(R.id.button_clear)
            imageButtonClear.isEnabled =
                documentController != null && documentController!!.hasPart()
        }
    }

    companion object {
        const val INPUT_MODE_KEY = "inputMode"
    }
}
