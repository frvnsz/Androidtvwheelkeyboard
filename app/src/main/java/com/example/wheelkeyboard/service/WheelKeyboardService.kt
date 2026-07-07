package com.example.wheelkeyboard.service

import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.inputmethodservice.InputMethodService
import com.example.wheelkeyboard.model.WheelKeyboardItem
import com.example.wheelkeyboard.model.WheelRotationState
import com.example.wheelkeyboard.view.WheelKeyboardView
import com.example.wheelkeyboard.voice.VoiceInputHelper

class WheelKeyboardService : InputMethodService() {
    private val handler = Handler(Looper.getMainLooper())
    private var wheelView: WheelKeyboardView? = null
    private var editorInfo: EditorInfo? = null
    private var rotationState: WheelRotationState? = null
    private val rotationRunnable = object : Runnable {
        override fun run() {
            val state = rotationState ?: return
            wheelView?.rotate(state.direction)
            handler.postDelayed(this, state.delayMillis())
        }
    }
    private val voiceInput by lazy {
        VoiceInputHelper(this, onText = { currentInputConnection?.commitText(it, 1) })
    }

    override fun onCreateInputView(): View = WheelKeyboardView(this).also { wheelView = it }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        editorInfo = info
        if (!isKeyboardInput(info)) {
            stopRotation()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        editorInfo = info
        if (isKeyboardInput(info)) {
            wheelView?.requestFocus()
        } else {
            stopRotation()
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        stopRotation()
        voiceInput.cancel()
        editorInfo = null
    }

    override fun onDestroy() {
        voiceInput.destroy()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onEvaluateInputViewShown(): Boolean = isKeyboardInput(editorInfo ?: currentInputEditorInfo)

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!shouldHandleRemoteInput()) {
            return false
        }
        WheelRotationState.directionFor(keyCode)?.let { direction ->
            startOrReverseRotation(direction)
            return true
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { moveCursor(-1); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { moveCursor(1); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { activateSelectedItem(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!shouldHandleRemoteInput()) {
            return false
        }
        if (WheelRotationState.directionFor(keyCode) != null) {
            stopRotation()
            return true
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> true
            else -> super.onKeyUp(keyCode, event)
        }
    }

    private fun shouldHandleRemoteInput(): Boolean =
        isInputViewShown && currentInputConnection != null && isKeyboardInput(editorInfo ?: currentInputEditorInfo)

    private fun isKeyboardInput(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return false
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT,
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> true
            else -> false
        }
    }

    private fun startOrReverseRotation(direction: Int) {
        val current = rotationState
        if (current?.direction == direction) return
        stopRotation()
        rotationState = WheelRotationState(direction = direction)
        wheelView?.rotate(direction)
        handler.postDelayed(rotationRunnable, rotationState?.delayMillis() ?: 360L)
    }

    private fun stopRotation() {
        rotationState = null
        handler.removeCallbacks(rotationRunnable)
    }

    private fun activateSelectedItem() {
        val ic = currentInputConnection ?: return
        when (val item = wheelView?.selectedItem()) {
            is WheelKeyboardItem.Letter -> ic.commitText(item.value.toString(), 1)
            is WheelKeyboardItem.Digit -> ic.commitText(item.value.toString(), 1)
            is WheelKeyboardItem.Punctuation -> ic.commitText(item.value.toString(), 1)
            WheelKeyboardItem.Space -> ic.commitText(" ", 1)
            WheelKeyboardItem.Delete -> ic.deleteSurroundingText(1, 0)
            WheelKeyboardItem.Search, WheelKeyboardItem.Done -> performCurrentEditorAction(ic)
            WheelKeyboardItem.VoiceSearch -> voiceInput.start()
            WheelKeyboardItem.ClearText -> ic.deleteSurroundingText(Int.MAX_VALUE, Int.MAX_VALUE)
            WheelKeyboardItem.ToggleSymbols, WheelKeyboardItem.ToggleLetters -> wheelView?.toggleMode()
            null -> Unit
        }
    }

    private fun performCurrentEditorAction(ic: InputConnection) {
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)?.takeIf { it != EditorInfo.IME_ACTION_UNSPECIFIED }
            ?: EditorInfo.IME_ACTION_DONE
        ic.performEditorAction(action)
    }

    private fun moveCursor(delta: Int) {
        val ic = currentInputConnection ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val textLength = extracted.text?.length ?: 0
        val selection = extracted.selectionEnd.coerceIn(0, textLength)
        val newPosition = (selection + delta).coerceIn(0, textLength)
        ic.setSelection(newPosition, newPosition)
        syncWheelToCursor(ic, delta)
    }

    private fun syncWheelToCursor(ic: InputConnection, delta: Int) {
        val adjacent = if (delta <= 0) ic.getTextBeforeCursor(1, 0) else ic.getTextAfterCursor(1, 0)
        val char = adjacent?.firstOrNull() ?: return
        wheelView?.setModeForCharacter(char)
    }
}
