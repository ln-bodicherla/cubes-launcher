package com.cubeslauncher.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import com.cubeslauncher.keyboard.R
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.keyboard.KeyboardPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class CubesKeyboardService : InputMethodService(), KeyboardActionListener {

    private lateinit var keyboardView: CubesKeyboardView
    private lateinit var rootView: View
    private var currentEditorInfo: EditorInfo? = null
    private val suggestionEngine = WordSuggestionEngine()
    private var lastSpaceTime = 0L
    private var lastSwipedWord: String? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // Preference flags
    private var autoCorrectEnabled = true
    private var autoCapitalizeEnabled = true
    private var doubleSpacePeriodEnabled = true
    private var showSuggestionsEnabled = true
    private var glideTypingEnabled = true
    private var numberRowEnabled = false
    private var voiceInputEnabled = true
    private var clipboardEnabled = true

    private var clipboardManager: KeyboardClipboardManager? = null

    override fun onCreate() {
        super.onCreate()
        AccentColorManager.init(this)
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        rootView = view
        keyboardView = view.findViewById(R.id.cubes_keyboard_view)
        keyboardView.listener = this

        loadPreferences()

        applyThemeAndPadding()
        return view
    }

    private fun loadPreferences() {
        val prefs = KeyboardPreferencesManager(this)
        val heightMode = runBlocking { prefs.keyboardHeight.first() }
        keyboardView.keyboardHeightMode = heightMode

        autoCorrectEnabled = runBlocking { prefs.keyboardAutoCorrect.first() }
        autoCapitalizeEnabled = runBlocking { prefs.keyboardAutoCapitalize.first() }
        doubleSpacePeriodEnabled = runBlocking { prefs.keyboardDoubleSpacePeriod.first() }
        showSuggestionsEnabled = runBlocking { prefs.keyboardShowSuggestions.first() }
        glideTypingEnabled = runBlocking { prefs.keyboardGlideTyping.first() }
        numberRowEnabled = runBlocking { prefs.keyboardNumberRow.first() }
        voiceInputEnabled = runBlocking { prefs.keyboardVoiceInput.first() }
        clipboardEnabled = runBlocking { prefs.keyboardClipboard.first() }

        keyboardView.glideTrailEnabled = runBlocking { prefs.keyboardGlideTrail.first() }
        keyboardView.glideTypingEnabled = glideTypingEnabled
        keyboardView.showVoiceInput = voiceInputEnabled

        if (clipboardEnabled && clipboardManager == null) {
            clipboardManager = KeyboardClipboardManager(this)
        }
    }

    private fun applyThemeAndPadding() {
        val prefs = KeyboardPreferencesManager(this)
        val theme = runBlocking { prefs.themeMode.first() }
        keyboardView.loadTheme()

        val bgColor = if (theme == "dark") 0xFF1A1A1A.toInt() else 0xFFF2F2F2.toInt()
        rootView.setBackgroundColor(bgColor)

        // Consistent bottom padding from system resources (stable value)
        val navBarHeight = getNavigationBarHeight()
        rootView.setPadding(0, 0, 0, navBarHeight * 2)
    }

    private fun getNavigationBarHeight(): Int {
        // Always use the stable resource-based value for consistency
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentEditorInfo = info

        // Reload preferences each time input starts
        loadPreferences()

        // Reset to appropriate QWERTY page
        keyboardView.currentPage = if (numberRowEnabled) {
            KeyboardLayouts.QWERTY_WITH_NUMBER_ROW
        } else {
            KeyboardLayouts.QWERTY
        }
        applyThemeAndPadding()
        lastSpaceTime = 0L

        // Auto-capitalize: check cursor position for sentence start
        keyboardView.shiftState = ShiftState.OFF
        if (autoCapitalizeEnabled) {
            checkAutoCapitalize()
        }

        // Set action label on enter key based on IME options
        keyboardView.actionLabel = when (info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> null
        }

        updateSuggestions()
    }

    override fun onKeyPress(key: Key) {
        val ic = currentInputConnection ?: return
        lastSwipedWord = null

        when (key.type) {
            KeyType.LETTER -> {
                if (key.outputChar != null) {
                    val ch = key.outputChar
                    val finalChar = if (keyboardView.shiftState != ShiftState.OFF && ch.isLetter()) {
                        ch.uppercaseChar()
                    } else ch
                    ic.commitText(finalChar.toString(), 1)
                } else {
                    // Emoji or multi-char label
                    ic.commitText(key.label, 1)
                }
                if (keyboardView.shiftState == ShiftState.SINGLE) {
                    keyboardView.shiftState = ShiftState.OFF
                }
                lastSpaceTime = 0L
                updateSuggestions()
            }
            KeyType.COMMA, KeyType.PERIOD -> {
                val ch = key.outputChar ?: return
                ic.commitText(ch.toString(), 1)
                lastSpaceTime = 0L
                if (ch == '.' && autoCapitalizeEnabled) {
                    checkAutoCapitalize()
                }
                updateSuggestions()
            }
            KeyType.SPACE -> {
                val now = System.currentTimeMillis()
                if (doubleSpacePeriodEnabled && now - lastSpaceTime < 300 && lastSpaceTime > 0) {
                    // Double-space → replace space with ". "
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    lastSpaceTime = 0L
                    if (autoCapitalizeEnabled) {
                        keyboardView.shiftState = ShiftState.SINGLE
                    }
                } else {
                    ic.commitText(" ", 1)
                    lastSpaceTime = now
                    learnCurrentWord()
                    if (autoCapitalizeEnabled) {
                        checkAutoCapitalize()
                    }
                }
                updateSuggestions()
            }
            KeyType.BACKSPACE -> {
                ic.deleteSurroundingText(1, 0)
                lastSpaceTime = 0L
                updateSuggestions()
            }
            KeyType.ENTER -> {
                val editorInfo = currentEditorInfo
                if (editorInfo != null) {
                    val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
                    if (action != EditorInfo.IME_ACTION_NONE &&
                        action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                        ic.performEditorAction(action)
                    } else {
                        ic.commitText("\n", 1)
                    }
                } else {
                    ic.commitText("\n", 1)
                }
                lastSpaceTime = 0L
                if (autoCapitalizeEnabled) {
                    checkAutoCapitalize()
                }
                updateSuggestions()
            }
            KeyType.SHIFT -> {
                // Shift state handled by the view
            }
            KeyType.SYMBOL_SWITCH -> {
                keyboardView.currentPage = KeyboardLayouts.SYMBOLS_1
                keyboardView.shiftState = ShiftState.OFF
                keyboardView.setSuggestions(emptyList())
            }
            KeyType.ABC_SWITCH -> {
                keyboardView.currentPage = if (numberRowEnabled) {
                    KeyboardLayouts.QWERTY_WITH_NUMBER_ROW
                } else {
                    KeyboardLayouts.QWERTY
                }
                updateSuggestions()
            }
            KeyType.MORE_SYMBOLS -> {
                keyboardView.currentPage = KeyboardLayouts.SYMBOLS_2
                keyboardView.setSuggestions(emptyList())
            }
            KeyType.EMOJI -> {
                keyboardView.currentPage = KeyboardLayouts.EMOJI_PAGE
            }
        }
    }

    override fun onKeyRelease(key: Key) {
        // No-op
    }

    override fun onKeyRepeat(key: Key) {
        if (key.type == KeyType.BACKSPACE) {
            currentInputConnection?.deleteSurroundingText(1, 0)
            updateSuggestions()
        }
    }

    override fun onSwipeComplete(keys: List<Key>) {
        if (!glideTypingEnabled) return
        val ic = currentInputConnection ?: return
        val suggestions = suggestionEngine.getSwipeSuggestions(keys, 5)
        if (suggestions.isEmpty()) return

        val bestMatch = suggestions.first()

        // Apply shift state
        val finalWord = when (keyboardView.shiftState) {
            ShiftState.SINGLE -> bestMatch.replaceFirstChar { it.uppercaseChar() }
            ShiftState.CAPS_LOCK -> bestMatch.uppercase()
            ShiftState.OFF -> bestMatch
        }

        // Delete any partially typed word before committing
        val currentWord = getCurrentWord()
        if (currentWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
        }

        ic.commitText("$finalWord ", 1)
        lastSwipedWord = finalWord
        lastSpaceTime = 0L

        if (keyboardView.shiftState == ShiftState.SINGLE) {
            keyboardView.shiftState = ShiftState.OFF
        }

        // Show top 3 suggestions in the bar
        val displaySuggestions = suggestions.take(3).map { word ->
            when (keyboardView.shiftState) {
                ShiftState.CAPS_LOCK -> word.uppercase()
                else -> word
            }
        }
        keyboardView.setSuggestions(displaySuggestions)
        if (autoCapitalizeEnabled) {
            checkAutoCapitalize()
        }
    }

    override fun onSuggestionSelected(suggestion: String) {
        val ic = currentInputConnection ?: return

        // If replacing a swiped word, delete the word + trailing space
        val swipedWord = lastSwipedWord
        if (swipedWord != null) {
            ic.deleteSurroundingText(swipedWord.length + 1, 0) // +1 for trailing space
            ic.commitText("$suggestion ", 1)
            lastSwipedWord = suggestion
            suggestionEngine.learn(suggestion)
            lastSpaceTime = 0L
            if (autoCapitalizeEnabled) checkAutoCapitalize()
            return
        }

        // Check if this is a clipboard item selection
        if (clipboardEnabled && clipboardManager != null) {
            val clipHistory = clipboardManager!!.getHistory()
            if (clipHistory.contains(suggestion)) {
                ic.commitText(suggestion, 1)
                lastSpaceTime = 0L
                if (autoCapitalizeEnabled) checkAutoCapitalize()
                updateSuggestions()
                return
            }
        }

        val currentWord = getCurrentWord()
        if (currentWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
        }
        ic.commitText("$suggestion ", 1)

        suggestionEngine.learn(suggestion)
        lastSpaceTime = 0L
        if (autoCapitalizeEnabled) checkAutoCapitalize()
        updateSuggestions()
    }

    private fun getCurrentWord(): String {
        val ic = currentInputConnection ?: return ""
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: return ""
        if (before.isEmpty()) return ""
        val lastSpace = before.lastIndexOf(' ')
        val lastNewline = before.lastIndexOf('\n')
        val lastSep = maxOf(lastSpace, lastNewline)
        return if (lastSep >= 0) before.substring(lastSep + 1) else before
    }

    private fun learnCurrentWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: return
        // Get the word before the last space (the word that was just completed)
        val trimmed = before.trimEnd()
        val lastSpace = trimmed.lastIndexOf(' ')
        val lastNewline = trimmed.lastIndexOf('\n')
        val lastSep = maxOf(lastSpace, lastNewline)
        val word = if (lastSep >= 0) trimmed.substring(lastSep + 1) else trimmed
        if (word.length >= 2) {
            suggestionEngine.learn(word)
        }
    }

    private fun updateSuggestions() {
        if (!showSuggestionsEnabled) {
            // When suggestions disabled and clipboard enabled, show clipboard items when idle
            if (clipboardEnabled && clipboardManager != null) {
                val currentWord = getCurrentWord()
                if (currentWord.isEmpty() && !currentPage().isEmoji) {
                    val clips = clipboardManager!!.getHistory().take(3)
                    if (clips.isNotEmpty()) {
                        keyboardView.setSuggestions(clips.map { if (it.length > 20) it.take(19) + "\u2026" else it })
                        return
                    }
                }
            }
            keyboardView.setSuggestions(emptyList())
            return
        }

        val currentWord = getCurrentWord()
        if (currentWord.isEmpty() || currentPage().isEmoji) {
            // Show clipboard items when idle and clipboard enabled
            if (clipboardEnabled && clipboardManager != null && !currentPage().isEmoji) {
                val clips = clipboardManager!!.getHistory().take(3)
                if (clips.isNotEmpty()) {
                    keyboardView.setSuggestions(clips.map { if (it.length > 20) it.take(19) + "\u2026" else it })
                    return
                }
            }
            keyboardView.setSuggestions(emptyList())
            return
        }

        val completions = suggestionEngine.getSuggestions(currentWord.lowercase(), 3)
        val display = mutableListOf<String>()

        // First slot: best completion (or raw word if no completions)
        if (completions.isNotEmpty()) {
            display.addAll(completions.take(3))
        }

        // Always include the raw typed word if it's not already in the list
        if (display.none { it.equals(currentWord, ignoreCase = true) } && currentWord.length >= 2) {
            display.add(0, currentWord)
            if (display.size > 3) display.removeAt(display.size - 1)
        }

        keyboardView.setSuggestions(display)
    }

    private fun currentPage(): KeyboardPage = keyboardView.currentPage

    private fun checkAutoCapitalize() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(3, 0)?.toString() ?: return

        // Auto-capitalize after ". " or "! " or "? " or at start of text
        if (before.isEmpty() ||
            before.endsWith(". ") ||
            before.endsWith("! ") ||
            before.endsWith("? ") ||
            before == "\n" ||
            before.endsWith("\n")) {
            if (keyboardView.shiftState == ShiftState.OFF) {
                keyboardView.shiftState = ShiftState.SINGLE
            }
        }
    }

    // --- Voice input ---

    override fun onVoiceInput() {
        if (!voiceInputEnabled) return
        if (isListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    keyboardView.isListening = true
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    keyboardView.isListening = false
                }
                override fun onError(error: Int) {
                    isListening = false
                    keyboardView.isListening = false
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    keyboardView.isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        val ic = currentInputConnection ?: return
                        ic.commitText(text, 1)
                        checkAutoCapitalize()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        // Show partial results as suggestions
                        keyboardView.setSuggestions(matches.take(3))
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        isListening = true
        keyboardView.isListening = true
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        keyboardView.isListening = false
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }
}
