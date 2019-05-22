// Copyright MyScript. All rights reserved.

package com.myscript.iink.demo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.TextView

import java.io.PrintWriter
import java.io.StringWriter


/**
 * This activity displays an error message when an uncaught exception is thrown within an activity
 * that installed the associated exception handler. Since this application targets developers it's
 * better to clearly explain what happened.
 * The code is inspired by:
 * https://trivedihardik.wordpress.com/2011/08/20/how-to-avoid-force-close-error-in-android/
 */
class ErrorActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_error)

        val messageView = findViewById<TextView>(R.id.error_message)
        messageView.text = intent.getStringExtra(INTENT_EXTRA_MESSAGE)

        val detailsView = findViewById<TextView>(R.id.error_details)
        detailsView.text = intent.getStringExtra(INTENT_EXTRA_DETAILS)
        detailsView.movementMethod = ScrollingMovementMethod()
    }

    private class ExceptionHandler(private val context: Activity) :
        Thread.UncaughtExceptionHandler {

        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            // Get the message of the root cause
            var rootCause = throwable
            while (rootCause.cause != null)
                rootCause = rootCause.cause!!
            val message = rootCause.message

            // Print the stack trace to a string
            val stackTraceWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stackTraceWriter))
            val stackTrace = stackTraceWriter.toString()

            // Launch the error activity with the message and stack trace
            if (message != null) {
                start(context, message, stackTrace)
            }

            // Kill the current activity
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        }
    }

    companion object {
        const val INTENT_EXTRA_MESSAGE = "message"
        const val INTENT_EXTRA_DETAILS = "details"

        fun start(context: Activity, message: String, details: String) {
            val intent = Intent(context, ErrorActivity::class.java)
            intent.putExtra(INTENT_EXTRA_MESSAGE, message)
            intent.putExtra(INTENT_EXTRA_DETAILS, details)
            context.startActivity(intent)
        }

        fun installHandler(context: Activity) {
            Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler(context))
        }
    }
}
