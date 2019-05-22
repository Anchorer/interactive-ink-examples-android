// Copyright MyScript. All rights reserved.

package com.myscript.iink.demo

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.myscript.iink.*
import com.myscript.iink.uireferenceimplementation.EditorView
import com.myscript.iink.uireferenceimplementation.ImageDrawer
import java.io.*
import java.util.*

class DocumentController(private val activity: Activity, private val editorView: EditorView?) {
    private val editor: Editor?

    private var currentFile: File? = null
    private var currentPackage: ContentPackage? = null
    private var currentPart: ContentPart? = null

    private val stateFile: File
    private var stateProperties: Properties? = null

    val partIndex: Int
        get() = if (currentPart == null) -1 else currentPackage!!.indexOfPart(currentPart!!)

    val partCount: Int
        get() = if (currentPackage == null) 0 else currentPackage!!.partCount

    val fileName: String
        get() = currentFile!!.name

    val savedFileName: String?
        get() = if (stateProperties != null) stateProperties!!.getProperty(
            DOCUMENT_CONTROLLER_STATE_SAVED_FILE_NAME
        ) else null

    val savedPartIndex: Int
        get() = if (stateProperties != null) Integer.valueOf(
            stateProperties!!.getProperty(
                DOCUMENT_CONTROLLER_STATE_SAVED_PART_INDEX
            )
        ) else 0

    init {
        this.editor = editorView?.editor

        currentFile = null
        currentPackage = null
        currentPart = null

        stateFile =
            File(activity.filesDir.path + File.separator + DOCUMENT_CONTROLLER_STATE_FILE_NAME)
        loadState()
    }

    fun close() {
        if (currentPart != null)
            currentPart!!.close()
        if (currentPackage != null)
            currentPackage!!.close()

        currentFile = null
        currentPackage = null
        currentPart = null
    }

    fun hasPart(): Boolean {
        return currentPart != null
    }

    private fun makeUntitledFilename(): String {
        var num = 0
        var name: String
        do {
            name = "File" + ++num + ".iink"
        } while (File(
                activity.filesDir,
                name
            ).exists() || currentFile != null && currentFile!!.name == name
        )
        return name
    }

    fun setPart(newFile: File?, newPackage: ContentPackage, newPart: ContentPart) {
        editor!!.renderer.setViewOffset(0f, 0f)
        editor.renderer.viewScale = 1f
        editor.part = newPart
        editorView?.visibility = View.VISIBLE

        if (currentPart != null && currentPart !== newPart)
            currentPart!!.close()
        if (currentPackage != null && currentPackage !== newPackage)
            currentPackage!!.close()

        currentFile = newFile
        currentPackage = newPackage
        currentPart = newPart

        activity.title = currentFile!!.name + " - " + currentPart!!.type
    }

    fun newPackage(): Boolean {
        val context = this.activity
        val fileName = makeUntitledFilename()
        val file = File(activity.filesDir, fileName)
        try {
            val newPackage = editor!!.engine.createPackage(file)
            newPart(file, newPackage, true)
        } catch (e: IOException) {
            Toast.makeText(context, "Failed to create package", Toast.LENGTH_LONG).show()
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, "Package already opened", Toast.LENGTH_LONG).show()
        }

        return true
    }

    fun openPackage(): Boolean {
        val files = activity.filesDir.listFiles()
        val context = this.activity

        if (files.size == 0) {
            Log.e(TAG, "Failed to list files in \"" + activity.filesDir + "\"")
            return false
        }
        val fileNames = arrayOfNulls<String>(files.size)
        for (i in files.indices)
            fileNames[i] = files[i].name
        val selected = intArrayOf(0)
        val dialogBuilder = AlertDialog.Builder(activity)
        dialogBuilder.setTitle(R.string.openPackage_title)
        dialogBuilder.setSingleChoiceItems(fileNames, selected[0]) { dialog, which ->
            selected[0] = which
        }
        dialogBuilder.setPositiveButton(R.string.ok) { dialog, which ->
            val newFile = files[selected[0]]

            try {
                val newPackage = editor!!.engine.openPackage(newFile)
                val newPart = newPackage.getPart(0)
                setPart(newFile, newPackage, newPart)
            } catch (e: IOException) {
                Toast.makeText(context, "Failed to open package", Toast.LENGTH_LONG).show()
            }
        }
        dialogBuilder.setNegativeButton(R.string.cancel, null)
        val dialog = dialogBuilder.create()
        dialog.show()
        return true
    }

    fun savePackage(): Boolean {
        if (currentPart == null)
            return false

        try {
            currentPart!!.getPackage().save()
            storeState()
        } catch (e: IOException) {
            Toast.makeText(this.activity, "Failed to save package", Toast.LENGTH_LONG).show()
        }

        return true
    }

    fun saveToTemp(): Boolean {
        if (currentPart == null)
            return false

        try {
            currentPart!!.getPackage().saveToTemp()
            storeState()
        } catch (e: IOException) {
            Toast.makeText(
                this.activity,
                "Failed to save package to temporary directory",
                Toast.LENGTH_LONG
            ).show()
        }

        return true
    }

    fun newPart(): Boolean {
        return if (currentPart == null) newPackage() else newPart(
            currentFile,
            currentPackage!!,
            false
        )
    }

    private fun newPart(
        targetFile: File?,
        targetPackage: ContentPackage,
        closeOnCancel: Boolean
    ): Boolean {
        val newPart = targetPackage.createPart("Text")
        setPart(targetFile, targetPackage, newPart)
        return true
    }

    fun openPart(fileName: String, indexOfPart: Int): Boolean {
        try {
            val file = File(activity.filesDir, fileName)
            val newPackage = editor!!.engine.openPackage(file)
            val newPart = newPackage.getPart(indexOfPart)

            setPart(file, newPackage, newPart)
        } catch (e: IOException) {
            Toast.makeText(
                this.activity,
                "Failed to open part for file \"$fileName\" with index $indexOfPart",
                Toast.LENGTH_LONG
            ).show()
        }

        return true
    }

    fun resetView(): Boolean {
        val renderer = editor!!.renderer
        renderer.setViewOffset(0f, 0f)
        renderer.viewScale = 1f
        editorView?.invalidate(renderer, EnumSet.allOf(IRenderTarget.LayerType::class.java))
        return true
    }

    fun zoomIn(): Boolean {
        val renderer = editor!!.renderer
        renderer.zoom(110.0f / 100.0f)
        editorView?.invalidate(renderer, EnumSet.allOf(IRenderTarget.LayerType::class.java))
        return true
    }

    fun zoomOut(): Boolean {
        val renderer = editor!!.renderer
        renderer.zoom(100.0f / 110.0f)
        editorView?.invalidate(renderer, EnumSet.allOf(IRenderTarget.LayerType::class.java))
        return true
    }

    fun convert(block: ContentBlock): Boolean {
        val supportedStates = editor!!.getSupportedTargetConversionStates(block)

        if (supportedStates.size == 0)
            return false

        editor.convert(block, supportedStates[0])

        return true
    }

    fun export(block: ContentBlock): Boolean {
        val mimeTypes = editor!!.getSupportedExportMimeTypes(block)

        if (mimeTypes.size == 0)
            return false

        val typeExtensions = ArrayList<String>()
        val typeDescriptions = ArrayList<String>()

        for (mimeType in mimeTypes) {
            val fileExtensions = mimeType.fileExtensions ?: continue

            val extensions = fileExtensions.split(" *, *".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()

            for (extension in extensions) {
                val extension_: String

                if (extension.startsWith("."))
                    extension_ = extension
                else
                    extension_ = ".$extension"

                typeExtensions.add(extension_)
                typeDescriptions.add(mimeType.getName() + " (*" + extension_ + ")")
            }
        }

        if (typeExtensions.isEmpty())
            return false

        val selected = intArrayOf(0)
        val dialogBuilder = AlertDialog.Builder(activity)

        dialogBuilder.setTitle(R.string.exportType_title)
        dialogBuilder.setSingleChoiceItems(
            typeDescriptions.toTypedArray(),
            selected[0]
        ) { dialog, which -> selected[0] = which }
        dialogBuilder.setPositiveButton(R.string.ok) { dialog, which ->
            export_(
                block,
                typeExtensions[selected[0]]
            )
        }
        dialogBuilder.setNegativeButton(R.string.cancel, null)

        val dialog = dialogBuilder.create()
        dialog.show()

        return true
    }

    private fun export_(block: ContentBlock, fileExtension: String): Boolean {
        val context = this.activity
        val fileHolder = arrayOfNulls<File>(1)
        val input = EditText(activity)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                var fileName = s.toString()

                if (!fileName.endsWith(fileExtension))
                    fileName = fileName + fileExtension

                val file = File(activity.filesDir, fileName)

                fileHolder[0] = file
                if (file.exists())
                    input.setTextColor(Color.RED)
                else
                    input.setTextColor(Color.BLACK)
            }
        })

        val filename = currentFile!!.name
        val dotPos = filename.lastIndexOf('.')
        val basename = if (dotPos > 0) filename.substring(0, dotPos) else filename
        input.setText(basename + fileExtension)

        val dialogBuilder = AlertDialog.Builder(activity)
        dialogBuilder.setTitle(R.string.exportPackage_title)
        dialogBuilder.setView(input)
        dialogBuilder.setPositiveButton(R.string.ok) { dialog, which ->
            val file = fileHolder[0]

            if (file != null) {
                try {
                    val imageDrawer = ImageDrawer()
                    imageDrawer.setImageLoader(editorView?.imageLoader)
                    editor!!.waitForIdle()
                    editor.export_(block, file.path, imageDrawer)
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to export file", Toast.LENGTH_LONG).show()
                }

            }
        }

        dialogBuilder.setNegativeButton(R.string.cancel, null)

        val dialog = dialogBuilder.create()
        dialog.show()

        return true
    }

    fun loadState() {
        var stream: InputStream? = null
        try {
            try {
                stream = FileInputStream(stateFile)
                try {
                    stateProperties = Properties()
                    stateProperties!!.load(stream)
                } catch (e: IOException) {
                    Log.e(
                        TAG,
                        "Failed to load state from streamed file: \"" + stateFile.absolutePath + "\""
                    )
                }

            } catch (e: FileNotFoundException) {
                // file has never been created
                stateProperties = null
            }

        } finally {
            try {
                stream?.close()
            } catch (e: IOException) {
                Log.e(
                    TAG,
                    "Failed to close stream loaded from file: \"" + stateFile.absolutePath + "\""
                )
            }

        }
    }

    private fun storeState() {
        stateProperties = Properties()
        stateProperties!!.setProperty(DOCUMENT_CONTROLLER_STATE_SAVED_FILE_NAME, fileName)
        stateProperties!!.setProperty(
            DOCUMENT_CONTROLLER_STATE_SAVED_PART_INDEX,
            Integer.toString(partIndex)
        )
        var stream: FileOutputStream? = null
        try {
            stream = FileOutputStream(stateFile)
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Failed to open stream for file: \"" + stateFile.absolutePath + "\"")
        }

        try {
            stateProperties!!.store(stream, "")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to store stream for file: \"" + stateFile.absolutePath + "\"")
        }

    }

    companion object {
        private val TAG = "DocumentController"
        private val DOCUMENT_CONTROLLER_STATE_FILE_NAME = "documentControllerState.properties"
        val DOCUMENT_CONTROLLER_STATE_SAVED_FILE_NAME = "content_package_file_name"
        private val DOCUMENT_CONTROLLER_STATE_SAVED_PART_INDEX = "content_package_part_index"
    }
}
