// Copyright MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation

import android.content.Context
import android.os.SystemClock
import android.support.v4.view.GestureDetectorCompat
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

import com.myscript.iink.Editor
import com.myscript.iink.IRenderTarget
import com.myscript.iink.PointerEvent
import com.myscript.iink.PointerEventType
import com.myscript.iink.PointerType
import com.myscript.iink.graphics.Point

import java.util.EnumSet

class InputController(
    context: Context,
    private val renderTarget: IRenderTarget,
    private val editor: Editor?
) : View.OnTouchListener, GestureDetector.OnGestureListener {
    @get:Synchronized
    @set:Synchronized
    var inputMode: Int = 0
    private val gestureDetector: GestureDetectorCompat
    @get:Synchronized
    @set:Synchronized
    var listener: IInputControllerListener? = null
    private val eventTimeOffset: Long

    init {
        listener = null
        inputMode = INPUT_MODE_AUTO
        gestureDetector = GestureDetectorCompat(context, this)

        val relTime = SystemClock.uptimeMillis()
        val currentTime = System.currentTimeMillis()
        eventTimeOffset = currentTime - relTime
    }

    private fun handleOnTouchForPointer(
        event: MotionEvent,
        actionMask: Int,
        pointerIndex: Int
    ): Boolean {
        val pointerId = event.getPointerId(pointerIndex)
        val pointerType = event.getToolType(pointerIndex)

        val inputMode = inputMode

        val iinkPointerType: PointerType
        if (inputMode == INPUT_MODE_FORCE_PEN) {
            iinkPointerType = PointerType.PEN
        } else if (inputMode == INPUT_MODE_FORCE_TOUCH) {
            iinkPointerType = PointerType.TOUCH
        } else {
            when (pointerType) {
                MotionEvent.TOOL_TYPE_STYLUS -> iinkPointerType = PointerType.PEN
                MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_MOUSE -> iinkPointerType =
                    PointerType.TOUCH
                else ->
                    // unsupported event type
                    return false
            }
        }

        if (iinkPointerType == PointerType.TOUCH) {
            gestureDetector.onTouchEvent(event)
        }

        val historySize = event.historySize

        when (actionMask) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                editor!!.pointerDown(
                    event.getX(pointerIndex),
                    event.getY(pointerIndex),
                    eventTimeOffset + event.eventTime,
                    event.pressure,
                    iinkPointerType,
                    pointerId
                )
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (historySize > 0) {
                    val pointerEvents = arrayOfNulls<PointerEvent>(historySize + 1)
                    for (i in 0 until historySize)
                        pointerEvents[i] = PointerEvent(
                            PointerEventType.MOVE,
                            event.getHistoricalX(pointerIndex, i),
                            event.getHistoricalY(pointerIndex, i),
                            eventTimeOffset + event.getHistoricalEventTime(i),
                            event.getHistoricalPressure(pointerIndex, i),
                            iinkPointerType,
                            pointerId
                        )
                    pointerEvents[historySize] = PointerEvent(
                        PointerEventType.MOVE,
                        event.getX(pointerIndex),
                        event.getY(pointerIndex),
                        eventTimeOffset + event.eventTime,
                        event.pressure,
                        iinkPointerType,
                        pointerId
                    )
                    editor!!.pointerEvents(pointerEvents, true)
                } else {
                    editor!!.pointerMove(
                        event.getX(pointerIndex),
                        event.getY(pointerIndex),
                        eventTimeOffset + event.eventTime,
                        event.pressure,
                        iinkPointerType,
                        pointerId
                    )
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                if (historySize > 0) {
                    val pointerEvents = arrayOfNulls<PointerEvent>(historySize)
                    for (i in 0 until historySize)
                        pointerEvents[i] = PointerEvent(
                            PointerEventType.MOVE,
                            event.getHistoricalX(pointerIndex, i),
                            event.getHistoricalY(pointerIndex, i),
                            eventTimeOffset + event.getHistoricalEventTime(i),
                            event.getHistoricalPressure(pointerIndex, i),
                            iinkPointerType,
                            pointerId
                        )
                    editor!!.pointerEvents(pointerEvents, true)
                }
                editor!!.pointerUp(
                    event.getX(pointerIndex),
                    event.getY(pointerIndex),
                    eventTimeOffset + event.eventTime,
                    event.pressure,
                    iinkPointerType,
                    pointerId
                )

                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                editor!!.pointerCancel(pointerId)
                return true
            }

            else -> return false
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (editor == null) {
            return false
        }

        val action = event.action
        val actionMask = action and MotionEvent.ACTION_MASK

        if (actionMask == MotionEvent.ACTION_POINTER_DOWN || actionMask == MotionEvent.ACTION_POINTER_UP) {
            val pointerIndex =
                action and MotionEvent.ACTION_POINTER_INDEX_MASK shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
            return handleOnTouchForPointer(event, actionMask, pointerIndex)
        } else {
            var consumed = false
            val pointerCount = event.pointerCount
            for (pointerIndex in 0 until pointerCount) {
                consumed = consumed || handleOnTouchForPointer(event, actionMask, pointerIndex)
            }
            return consumed
        }
    }

    override fun onDown(event: MotionEvent): Boolean {
        return false
    }

    override fun onShowPress(event: MotionEvent) {
        // no-op
    }

    override fun onSingleTapUp(event: MotionEvent): Boolean {
        return false
    }

    override fun onLongPress(event: MotionEvent) {
        val x = event.x
        val y = event.y
        val listener = listener
        listener?.onLongPress(x, y, editor!!.hitBlock(x, y))
    }

    override fun onScroll(
        e1: MotionEvent,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (editor!!.isScrollAllowed) {
            val oldOffset = editor.renderer.viewOffset
            val newOffset = Point(oldOffset.x + distanceX, oldOffset.y + distanceY)
            editor.clampViewOffset(newOffset)
            editor.renderer.setViewOffset(newOffset.x, newOffset.y)
            renderTarget.invalidate(
                editor.renderer,
                EnumSet.allOf(IRenderTarget.LayerType::class.java)
            )
            return true
        }
        return false
    }

    override fun onFling(
        e1: MotionEvent,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return false
    }

    companion object {
        val INPUT_MODE_NONE = -1
        val INPUT_MODE_FORCE_PEN = 0
        val INPUT_MODE_FORCE_TOUCH = 1
        val INPUT_MODE_AUTO = 2
    }
}
