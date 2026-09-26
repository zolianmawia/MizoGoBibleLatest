package com.zoliana.khampat.mizobible.ui.transform

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.utils.ThemeHelper
import com.zoliana.khampat.mizobible.data.BibleVerse
import com.zoliana.khampat.mizobible.data.Bookmark
import com.zoliana.khampat.mizobible.data.MembershipType
import com.zoliana.khampat.mizobible.data.Note
import com.zoliana.khampat.mizobible.data.Pin
import com.zoliana.khampat.mizobible.databinding.ItemBibleVerseBinding
import java.util.Locale

class BibleAdapter(private val onVerseClick: (BibleVerse, View) -> Unit) :
    ListAdapter<BibleVerse, BibleAdapter.VerseViewHolder>(VerseDiffCallback()) {

    private var fontSettings = FontSettings()
    private val selectedVerses = mutableSetOf<Int>()
    private var pins = listOf<Pin>()
    private var bookmarks = listOf<Bookmark>()
    private var notes = listOf<Note>()
    private var highlightId: Int? = null
    private var highlightVerseNumber: String? = null
    private var membershipType: MembershipType = MembershipType.FREE
    private var sidePadding: Float = 0f

    fun setFontSettings(settings: FontSettings) {
        this.fontSettings = settings
        notifyDataSetChanged()
    }

    fun setMembership(type: MembershipType) {
        this.membershipType = type
        notifyDataSetChanged()
    }

fun setPins(pins: List<Pin>) {
    this.pins = pins
    notifyDataSetChanged()
}

    fun setBookmarks(bookmarks: List<Bookmark>) {
        this.bookmarks = bookmarks
        notifyDataSetChanged()
    }

    fun setNotes(notes: List<Note>) {
        this.notes = notes
        notifyDataSetChanged()
    }

    fun setHighlight(id: Int?, verseNum: String? = null) {
        this.highlightId = id
        this.highlightVerseNumber = verseNum
        notifyDataSetChanged()
    }

    fun setSidePadding(padding: Float) {
        this.sidePadding = padding
        notifyDataSetChanged()
    }

    fun setSelection(verseId: Int, isSelected: Boolean) {
        if (isSelected) {
            selectedVerses.add(verseId)
        } else {
            selectedVerses.remove(verseId)
        }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedVerses.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerseViewHolder {
        val binding = ItemBibleVerseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VerseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VerseViewHolder, position: Int) {
        val verse = getItem(position)
        val vId = verse.id ?: 0
        val isSelected = selectedVerses.contains(vId)

        // Matching logic robust leh zual nan (Book name normalization)
        fun normalize(s: String?): String {
            if (s == null) return ""
            return s.trim().lowercase(Locale.ROOT)
                .replace("[áàâ]".toRegex(), "a").replace("[éèê]".toRegex(), "e")
                .replace("[íìî]".toRegex(), "i").replace("[óòô]".toRegex(), "o")
                .replace("[úùû]".toRegex(), "u").replace("ṭ", "t")
                .replace("[^a-z0-9]".toRegex(), "")
        }

        fun bookRef(b: String?): String {
            var res = normalize(b)
            if (res.startsWith("iii")) res = "3" + res.substring(3)
            else if (res.startsWith("ii")) res = "2" + res.substring(2)
            else if (res.startsWith("i") && !res.startsWith("isai") && !res.startsWith("iosu")) {
                res = "1" + res.substring(1)
            }
            return res
        }

        fun getVerseNum(v: String?): Int? {
            if (v == null) return null
            return v.trim().takeWhile { it.isDigit() }.toIntOrNull()
        }

        fun isMatch(
            dbBook: String?,
            itemBook: String?,
            dbChapter: Int?,
            itemChapter: Int?,
            dbVerse: String?,
            itemVerse: String?
        ): Boolean {
            if (dbChapter != itemChapter) return false
            if (getVerseNum(dbVerse) != getVerseNum(itemVerse)) return false
            val b1 = bookRef(dbBook)
            val b2 = bookRef(itemBook)
            return b1 == b2 || (b1.length >= 3 && b2.length >= 3 && (b1.startsWith(b2.substring(0, 3)) || b2.startsWith(b1.substring(0, 3))))
        }

        val pin = pins.find {
            it.verseId == vId || isMatch(verse.book, it.book, verse.chapter, it.chapter, verse.verse, it.verse.toString())
        }

        val bookmark = bookmarks.find {
            it.verseId == vId || isMatch(verse.book, it.book, verse.chapter, it.chapter, verse.verse, it.verse.toString())
        }

        // Highlight matching logic
        val isIdMatch = vId != 0 && vId == highlightId
        val isNumMatch = highlightVerseNumber != null && getVerseNum(verse.verse) == getVerseNum(highlightVerseNumber)
        val shouldHighlight = isIdMatch || isNumMatch

        holder.bind(
            verse,
            fontSettings,
            isSelected,
            pin,
            bookmark,
            null,
            shouldHighlight,
            membershipType,
            sidePadding,
            onVerseClick
        )
    }

    class VerseViewHolder(private val binding: ItemBibleVerseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var bgAnimator: ValueAnimator? = null

        fun bind(
            verse: BibleVerse,
            settings: FontSettings,
            isSelected: Boolean,
            pin: Pin?,
            bookmark: Bookmark?,
            note: Note?,
            shouldHighlight: Boolean,
            membershipType: MembershipType,
            sidePadding: Float,
            onClick: (BibleVerse, View) -> Unit
        ) {

            bgAnimator?.cancel()
            val isHeading = verse.verse == "0"
            val isPlaceholder = verse.type == "placeholder"
            val density = binding.root.resources.displayMetrics.density

            if (isHeading) {
                binding.textVerseNumber.visibility = View.GONE
                binding.textVerseContent.gravity = Gravity.CENTER
                val basePadding = (16 * density).toInt()
                binding.layoutVerseMain.setPadding(basePadding, 48, basePadding, 24)
            } else {
                binding.textVerseNumber.visibility = View.GONE
                binding.textVerseContent.gravity = Gravity.START
                val hPadding = (sidePadding * density).toInt()
                val vPadding = (6 * density).toInt()
                binding.layoutVerseMain.setPadding(hPadding, vPadding, hPadding, vPadding)
            }

            if (isPlaceholder) {
                binding.textVerseContent.text = ""
                binding.root.setOnClickListener(null)
                binding.root.setBackgroundColor(Color.TRANSPARENT)
                binding.imgPinIndicator.visibility = View.GONE
                return
            }

            val verseNum = verse.verse ?: ""
            val rawText = verse.text ?: ""
            val fullText = if (isHeading || verseNum == "0") rawText else "$verseNum $rawText"
            val spannable = SpannableString(fullText)

            val typedValue = TypedValue()
            val theme = binding.root.context.theme
            
            // Base text colors: Guaranteed contrast with effective verse background
            val context = binding.root.context
            val customFontColor = try {
                if (settings.fontColor.isNotBlank() && settings.fontColor != "default") {
                    Color.parseColor(settings.fontColor)
                } else null
            } catch (e: Exception) { null }
            val effectiveVerseBg = ThemeHelper.getEffectiveToolbarColor(context)
            val effectiveTextColor = ThemeHelper.getContrastingTextColor(effectiveVerseBg, customFontColor)
            val textColor = effectiveTextColor
            
            val rawPrimary = if (theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)) typedValue.data else Color.parseColor("#1976D2")
            val primaryColor = ThemeHelper.getContrastingVerseNumberColor(effectiveVerseBg, rawPrimary)
            val selectionColor = if (theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)) typedValue.data else Color.parseColor("#FFE0B2")
            val defaultBookmarkColor = if (theme.resolveAttribute(com.google.android.material.R.attr.colorSecondaryContainer, typedValue, true)) typedValue.data else Color.parseColor("#FFF9C4")

            if (!isHeading && verseNum != "0" && verseNum.isNotEmpty()) {
                val end = verseNum.length
                spannable.setSpan(ForegroundColorSpan(primaryColor), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(RelativeSizeSpan(0.85f), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(StyleSpan(Typeface.BOLD), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(SuperscriptSpan(), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            val contentStart = if (isHeading || verseNum == "0") 0 else verseNum.length + 1
            if (pin != null && contentStart < spannable.length) {
                val pinColor = try { Color.parseColor(pin.color) } catch (e: Exception) { Color.RED }
                spannable.setSpan(WavyUnderlineSpan(pinColor), contentStart, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            val isBookmarked = bookmark != null
            val savedBookmarkColor = if (bookmark != null) {
                if (membershipType == MembershipType.FREE) {
                    ThemeHelper.getSoftBookmarkColor("#E0E0E0", effectiveVerseBg)
                } else {
                    ThemeHelper.getSoftBookmarkColor(bookmark.color, effectiveVerseBg)
                }
            } else defaultBookmarkColor

            val normalBgColor = when {
                isSelected -> selectionColor
                isBookmarked -> savedBookmarkColor
                else -> Color.TRANSPARENT
            }

            val isHighlighted = isSelected || isBookmarked
            val finalTextColor = if (isHighlighted) {
                ThemeHelper.getContrastingTextColor(normalBgColor, customFontColor)
            } else {
                textColor
            }

            if (isHighlighted && !isHeading && verseNum != "0" && verseNum.isNotEmpty()) {
                val highlightedVNumColor = ThemeHelper.getContrastingVerseNumberColor(normalBgColor, rawPrimary)
                spannable.setSpan(ForegroundColorSpan(highlightedVNumColor), 0, verseNum.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            binding.textVerseContent.text = spannable
            binding.textVerseContent.setTextColor(finalTextColor)

            if (shouldHighlight) {
                bgAnimator = ValueAnimator.ofObject(ArgbEvaluator(), normalBgColor, Color.parseColor("#80FF0000")).apply {
                    duration = 500
                    repeatCount = 7
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { animator ->
                        binding.root.setBackgroundColor(animator.animatedValue as Int)
                    }
                    start()
                }
            } else {
                binding.root.setBackgroundColor(normalBgColor)
            }

            if (pin != null) {
                binding.imgPinIndicator.visibility = View.VISIBLE
                try {
                    binding.imgPinIndicator.setColorFilter(Color.parseColor(pin.color))
                } catch (e: Exception) {
                    binding.imgPinIndicator.setColorFilter(Color.RED)
                }
            } else {
                binding.imgPinIndicator.visibility = View.GONE
            }

            val baseSize = if (settings.fontSize < 12f) 18f else settings.fontSize
            if (isHeading) binding.textVerseContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSize + 2f) else binding.textVerseContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSize)

            val style = if (isHeading) Typeface.BOLD else when {
                settings.isBold && settings.isItalic -> Typeface.BOLD_ITALIC; settings.isBold -> Typeface.BOLD; settings.isItalic -> Typeface.ITALIC; else -> Typeface.NORMAL
            }

            val tf = try {
                when (settings.fontFamily) {
                    "Default" -> Typeface.create(Typeface.DEFAULT, style)
                    "Sans Serif" -> Typeface.create(Typeface.SANS_SERIF, style)
                    "Serif", "Times New Roman" -> Typeface.create(Typeface.SERIF, style)
                    "Monospace" -> Typeface.create(Typeface.MONOSPACE, style)
                    else -> {
                        val extensions = listOf(".ttf", ".otf")
                        var assetTf: Typeface? = null
                        for (ext in extensions) {
                            try {
                                assetTf = Typeface.createFromAsset(binding.root.context.assets, "fonts/${settings.fontFamily}$ext")
                                break
                            } catch (e: Exception) { }
                        }
                        if (assetTf != null) Typeface.create(assetTf, style) else Typeface.create(Typeface.SERIF, style)
                    }
                }
            } catch (e: Exception) {
                Typeface.create(Typeface.SERIF, style)
            }

            binding.textVerseContent.typeface = tf
            binding.textVerseContent.letterSpacing = settings.letterSpacing
            val mult = if (settings.lineHeight <= 1.05f) 1.28f else settings.lineHeight
            binding.textVerseContent.setLineSpacing((2 * density), mult)
            binding.root.setOnClickListener { onClick(verse, binding.root) }
        }

        private fun isColorDark(color: Int): Boolean {
            val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
            return darkness >= 0.5
        }
    }

    class VerseDiffCallback : DiffUtil.ItemCallback<BibleVerse>() {
        override fun areItemsTheSame(oldItem: BibleVerse, newItem: BibleVerse): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BibleVerse, newItem: BibleVerse): Boolean = oldItem == newItem
    }
}
