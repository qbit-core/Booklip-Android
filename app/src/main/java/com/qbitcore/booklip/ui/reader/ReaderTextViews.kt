package com.qbitcore.booklip.ui.reader

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.qbitcore.booklip.model.Highlight
import com.qbitcore.booklip.tts.CharRange

/** Everything painted behind the text; none of it moves a line. */
class Decorations(
    val highlights: List<Highlight>,
    val search: SearchState?,
    val spoken: CharRange?,
    val spokenColor: Int,
) {
    companion object {
        val SEARCH_COLOR = Color(0xFFFFD60A).copy(alpha = 0.45f).toArgb()
        val SEARCH_CURRENT_COLOR = Color(0xFFFF9500).copy(alpha = 0.75f).toArgb()
        const val HIGHLIGHT_ALPHA = 0.38f
    }
}

/** What the text-selection menu can do. Offsets are in the book's text. */
class SelectionActions(
    val onHighlight: (start: Int, end: Int) -> Unit,
    val onRemoveHighlight: (start: Int, end: Int) -> Unit,
    val hasHighlight: (start: Int, end: Int) -> Boolean,
    /** Show the dictionary for the selected word(s). */
    val onDefine: (term: String) -> Unit,
)

private class DecorSpan(color: Int) : BackgroundColorSpan(color)

/** What a reader TextView currently shows, so rebinding the same block is a no-op (and keeps its selection). */
private class Binding(val doc: ReaderDocument, val start: Int, val end: Int, val spec: LayoutSpec)

fun newReaderTextView(context: Context): TextView = TextView(context).apply {
    gravity = Gravity.TOP or Gravity.START
    setTextIsSelectable(false)
    // The system adds a light-grey highlight colour by default; keep it subtle on every theme.
    highlightColor = 0x553A8DFF
}

/**
 * Shows `doc.text[start, end)` in this view, laid out per [spec]. Selection is
 * only possible in highlight mode: a plain reading view must never select
 * text (or swallow taps and swipes) by accident.
 */
fun TextView.bind(
    doc: ReaderDocument,
    start: Int,
    end: Int,
    spec: LayoutSpec,
    textColor: Int,
    selectable: Boolean,
    decorations: Decorations,
    actions: SelectionActions,
) {
    val bound = tag as? Binding
    if (bound == null || bound.doc !== doc || bound.start != start || bound.end != end || bound.spec != spec) {
        spec.applyTo(this)
        setText(SpannableString(ReaderLayout.styledText(doc, start, end, spec)), TextView.BufferType.SPANNABLE)
        tag = Binding(doc, start, end, spec)
    }
    setTextColor(textColor)
    if (isTextSelectable != selectable) setTextIsSelectable(selectable)
    customSelectionActionModeCallback = if (selectable) selectionCallback(start, actions) else null
    decorate(start, end, decorations)
}

private fun TextView.decorate(start: Int, end: Int, decorations: Decorations) {
    val spannable = text as? Spannable ?: return
    for (span in spannable.getSpans(0, spannable.length, DecorSpan::class.java)) spannable.removeSpan(span)

    fun paint(from: Int, to: Int, color: Int) {
        val s = maxOf(from, start) - start
        val e = minOf(to, end) - start
        if (e > s) spannable.setSpan(DecorSpan(color), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    for (h in decorations.highlights) {
        if (h.location >= end || h.end <= start) continue
        paint(h.location, h.end, Color(h.color.argb).copy(alpha = Decorations.HIGHLIGHT_ALPHA).toArgb())
    }
    decorations.search?.let { search ->
        // Only the matches inside this block — a book can hold thousands.
        var i = indexOfFirstAtLeast(search.matches, start - search.length + 1)
        while (i < search.matches.size && search.matches[i] < end) {
            val match = search.matches[i]
            paint(match, match + search.length, if (i == search.index) Decorations.SEARCH_CURRENT_COLOR else Decorations.SEARCH_COLOR)
            i++
        }
    }
    decorations.spoken?.let { paint(it.start, it.end, decorations.spokenColor) }
}

private const val MENU_HIGHLIGHT = 0x7B01
private const val MENU_REMOVE = 0x7B02
private const val MENU_DEFINE = 0x7B03

private fun TextView.selectionCallback(blockStart: Int, actions: SelectionActions) = object : ActionMode.Callback {
    private fun selection(): IntRange? {
        val s = minOf(selectionStart, selectionEnd)
        val e = maxOf(selectionStart, selectionEnd)
        return if (s >= 0 && e > s) (blockStart + s)..(blockStart + e) else null
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_HIGHLIGHT, 0, "Highlight")
        menu.add(Menu.NONE, MENU_DEFINE, 2, "Define")
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val range = selection()
        val overlaps = range != null && actions.hasHighlight(range.first, range.last)
        val existing = menu.findItem(MENU_REMOVE)
        if (overlaps && existing == null) menu.add(Menu.NONE, MENU_REMOVE, 1, "Remove Highlight")
        else if (!overlaps && existing != null) menu.removeItem(MENU_REMOVE)
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val range = selection() ?: return false
        when (item.itemId) {
            MENU_HIGHLIGHT -> actions.onHighlight(range.first, range.last)
            MENU_REMOVE -> actions.onRemoveHighlight(range.first, range.last)
            MENU_DEFINE -> {
                val term = text.subSequence(range.first - blockStart, range.last - blockStart).toString()
                    .trim().trim { !it.isLetterOrDigit() }
                if (term.isNotEmpty()) actions.onDefine(term)
            }
            else -> return false
        }
        mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {}
}
