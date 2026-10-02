package com.zoliana.khampat.mizobible.ui.transform

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.zoliana.khampat.mizobible.data.BibleRepository
import com.zoliana.khampat.mizobible.data.BibleVerse
import com.zoliana.khampat.mizobible.data.Bookmark
import com.zoliana.khampat.mizobible.data.MemberInfo
import com.zoliana.khampat.mizobible.data.MembershipType
import com.zoliana.khampat.mizobible.data.Note
import com.zoliana.khampat.mizobible.data.Pin
import com.zoliana.khampat.mizobible.data.ReadingLog
import com.zoliana.khampat.mizobible.utils.ThemeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class TransformViewModel(
    val repository: BibleRepository,
    application: Application
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("bible_prefs", Context.MODE_PRIVATE)
    private val userPrefs = application.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    private val ADMIN_EMAIL = "zlphoto02@gmail.com"

    private val _membershipType = MutableStateFlow(MembershipType.FREE)
    val membershipType: StateFlow<MembershipType> = _membershipType

    private val _silverMembers = MutableStateFlow<List<MemberInfo>>(emptyList())
    val silverMembers: StateFlow<List<MemberInfo>> = _silverMembers

    private val _goldMembers = MutableStateFlow<List<MemberInfo>>(emptyList())
    val goldMembers: StateFlow<List<MemberInfo>> = _goldMembers

    sealed class UiEvent {
        object ShowUpgradeDialog : UiEvent()
    }

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private var premiumListener: ListenerRegistration? = null
    private var isDownloadingFromCloud = false

    val currentVersion = MutableStateFlow(prefs.getString("last_version", "MzOV") ?: "MzOV")
    val currentBook = MutableStateFlow(prefs.getString("last_book", "Genesis") ?: "Genesis")
    val currentChapter = MutableStateFlow(prefs.getInt("last_chapter", 1))
    val lastVerse = MutableStateFlow(prefs.getString("last_verse", "1") ?: "1")
    var isInitialLoad = true

    val isSplitMode = MutableStateFlow(prefs.getBoolean("is_split_mode", false))
    val splitVersion = MutableStateFlow(prefs.getString("last_split_version", "KJV") ?: "KJV")

    private val _isVerticalSplit = MutableStateFlow(prefs.getBoolean("vertical_split", true))
    val isVerticalSplit: StateFlow<Boolean> = _isVerticalSplit

    val splitRatio = MutableStateFlow(prefs.getFloat("split_ratio", 0.5f))

    fun updateSplitRatio(ratio: Float) {
        splitRatio.value = ratio
        prefs.edit().putFloat("split_ratio", ratio).apply()
    }

    fun toggleSplitOrientation() {
        val newVal = !_isVerticalSplit.value
        _isVerticalSplit.value = newVal
        prefs.edit().putBoolean("vertical_split", newVal).apply()
    }

    val fontSettings = MutableStateFlow<FontSettings>(loadFontSettings())
    val highlightVerseId = MutableStateFlow<Int?>(null)
    val highlightVerseNumber = MutableStateFlow<String?>(null)

    val sidePadding = MutableStateFlow(prefs.getFloat("side_padding", 0f))
    val keepScreenOn = MutableStateFlow(prefs.getBoolean("keep_screen_on", false))

    val isEyeProtectionEnabled = MutableStateFlow(prefs.getBoolean("eye_protection_enabled", false))

    // Search State
    val searchQuery = MutableStateFlow("")
    val searchResults = MutableStateFlow<List<BibleVerse>>(emptyList())
    val searchFuzzyEnabled = MutableStateFlow(false)

    // Copy Settings
    val copyIncludeReference = MutableStateFlow(prefs.getBoolean("copy_include_ref", true))
    val copyReferenceAtBottom = MutableStateFlow(prefs.getBoolean("copy_ref_bottom", false))
    val copyHideVerseNumbers = MutableStateFlow(prefs.getBoolean("copy_hide_verse_numbers", false))

    val availableVersions: LiveData<List<String>> = repository.getAllVersions().asLiveData()
    val allReadingLogs: LiveData<List<ReadingLog>> = repository.getAllReadingLogs().asLiveData()

    // Pending Payment Persistence
    var pendingMembershipType: MembershipType? = null
    var pendingName: String? = null
    var pendingAddress: String? = null
    var pendingPhone: String? = null
    var pendingEmail: String? = null

    // Navigation History (Persisted)
    private val backStack = mutableListOf<NavHistoryItem>()
    private val forwardStack = mutableListOf<NavHistoryItem>()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward

    // Database Loading Progress
    private val _databaseLoadingProgress = MutableStateFlow(0)
    val databaseLoadingProgress: StateFlow<Int> = _databaseLoadingProgress

    // Persistence Keys
    private val KEY_BACK_STACK = "nav_history_back_v5"
    private val KEY_FORWARD_STACK = "nav_history_forward_v5"

    // Limits
    val totalLimit: LiveData<Int> = _membershipType.map {
        when (it) {
            MembershipType.FREE -> 100
            MembershipType.SILVER -> 1000
            MembershipType.GOLD -> 999999
        }
    }.asLiveData()

    val pinLimit: LiveData<Int> = _membershipType.map {
        when (it) {
            MembershipType.FREE -> 50
            MembershipType.SILVER -> 200
            MembershipType.GOLD -> 999999
        }
    }.asLiveData()

    // Data Observers
    val allBookmarks = repository.getAllBookmarks().asLiveData()
    val allPins = repository.getAllPins().asLiveData()
    val allNotes = repository.getAllNotes().asLiveData()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bibleVerses: LiveData<List<BibleVerse>> =
        combine(currentVersion, currentBook, currentChapter) { v, b, c -> Triple(v, b, c) }
            .flatMapLatest { (version, book, chapter) ->
                repository.getVerses(
                    version,
                    getTranslatedBookName(book, version),
                    chapter
                )
            }
            .asLiveData()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val splitBibleVerses: LiveData<List<BibleVerse>> =
        combine(splitVersion, currentVersion, currentBook, currentChapter) { sv, cv, b, c ->
            Triple(sv, cv, b to c)
        }.flatMapLatest { (sVersion, cVersion, bookChapter) ->
            val (book, chapter) = bookChapter
            
            val masterBook = getTranslatedBookName(book, cVersion)
            
            // Master flow is the current version
            val masterFlow = repository.getVerses(cVersion, masterBook, chapter)
            
            masterFlow.flatMapLatest { masterList ->
                // Try getting verses for other version with translated name
                val otherBook = getTranslatedBookName(book, sVersion)
                val otherFlowTranslated = repository.getVerses(sVersion, otherBook, chapter)
                
                otherFlowTranslated.flatMapLatest { otherListTranslated ->
                    // If empty and names are different, try original name
                    val finalOtherFlow = if (otherListTranslated.isEmpty() && !otherBook.equals(book, ignoreCase = true)) {
                        repository.getVerses(sVersion, book, chapter)
                    } else {
                        kotlinx.coroutines.flow.flowOf(otherListTranslated)
                    }
                    
                    finalOtherFlow.map { otherList ->
                        if (sVersion.equals(cVersion, ignoreCase = true)) {
                            return@map otherList
                        }
                        if (masterList.isEmpty()) {
                            return@map otherList
                        }

                        val aligned = mutableListOf<BibleVerse>()
                        val otherMap = otherList.groupBy { it.verse?.trim() ?: "0" }
                        
                        masterList.forEach { m ->
                            val mVerseNum = m.verse?.trim() ?: "0"
                            if (mVerseNum == "0") {
                                val matchingOther = otherMap["0"]?.firstOrNull()
                                aligned.add(matchingOther ?: m.copy(type = "pericope", text = "", id = -1))
                            } else {
                                val matches = otherMap[mVerseNum]
                                if (!matches.isNullOrEmpty()) {
                                    aligned.addAll(matches)
                                } else {
                                    aligned.add(
                                        BibleVerse(
                                            id = -2,
                                            type = "missing",
                                            book = book,
                                            chapter = chapter,
                                            verse = mVerseNum,
                                            text = "[...]",
                                            normalizedText = ""
                                        )
                                    )
                                }
                            }
                        }
                        if (aligned.isEmpty() && otherList.isNotEmpty()) otherList else aligned
                    }
                }
            }
        }.asLiveData()

    init {
        val savedType =
            userPrefs.getString("membership_type", MembershipType.FREE.name)
                ?: MembershipType.FREE.name
        _membershipType.value =
            try {
                MembershipType.valueOf(savedType)
            } catch (e: Exception) {
                MembershipType.FREE
            }

        loadHistory()

        viewModelScope.launch {
            startPremiumStatusListener()
        }

        startPublicMembersListener()

        viewModelScope.launch {
            currentBook.collectLatest {
                prefs.edit().putString("last_book", it).apply()
                if (!isInitialLoad) lastVerse.value = "1"
            }
        }
        viewModelScope.launch {
            currentChapter.collectLatest {
                prefs.edit().putInt("last_chapter", it).apply()
                if (!isInitialLoad) lastVerse.value = "1"
            }
        }
        viewModelScope.launch {
            lastVerse.collectLatest {
                prefs.edit().putString("last_verse", it).apply()
            }
        }
        viewModelScope.launch {
            currentVersion.collectLatest {
                val normalized =
                    if (it.lowercase() in listOf(
                            "pericope",
                            "mgb",
                            "verse",
                            "mizogobible"
                        )
                    ) "MzOV" else it
                prefs.edit().putString("last_version", normalized).apply()
            }
        }
        viewModelScope.launch {
            isSplitMode.collectLatest {
                prefs.edit().putBoolean("is_split_mode", it).apply()
            }
        }
        viewModelScope.launch {
            splitVersion.collectLatest {
                prefs.edit().putString("last_split_version", it).apply()
            }
        }

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            startPremiumStatusListener()
            if (auth.currentUser != null) syncFromCloud()
        }
    }

    private suspend fun migrateNotesToBookmarks() {
        val notes = repository.getAllNotes().first()
        if (notes.isNotEmpty()) {
            notes.forEach { note ->
                val bookmark = Bookmark(
                    verseId = note.verseId,
                    book = note.book,
                    chapter = note.chapter,
                    verse = note.verse,
                    text = note.bibleText.ifEmpty { "Bible Verse" },
                    version = note.version,
                    note = note.text,
                    color = note.color,
                    timestamp = note.timestamp
                )
                repository.insertBookmark(bookmark)
            }
            repository.deleteAllNotes()
        }
    }

    fun startPremiumStatusListener() {
        premiumListener?.remove()
        val user = FirebaseAuth.getInstance().currentUser ?: return

        if (user.email?.lowercase()?.trim() == ADMIN_EMAIL.lowercase()) {
            setMembership(MembershipType.GOLD, Long.MAX_VALUE, shouldSync = false)
            return
        }

        // Use lowercase email if exists, otherwise UID
        val id = user.email?.lowercase()?.trim() ?: user.uid
        premiumListener = FirebaseFirestore.getInstance().collection("premium_users")
            .document(id)
            .addSnapshotListener { doc, e ->
                if (e != null) return@addSnapshotListener // Ignore errors to prevent flickering

                if (doc != null && doc.exists()) {
                    if (doc.getBoolean("active") == true) {
                        val typeStr = doc.getString("type") ?: MembershipType.SILVER.name

                        // FIX: Safely map Firestore strings to MembershipType enum
                        val membership = when (typeStr.uppercase()) {
                            "SILVER", "PATRON" -> MembershipType.SILVER
                            "GOLD", "LIVE" -> MembershipType.GOLD
                            "FREE" -> MembershipType.FREE
                            else -> try {
                                MembershipType.valueOf(typeStr)
                            } catch (ex: Exception) {
                                MembershipType.FREE
                            }
                        }

                        val expiresAt = doc.getLong("expiresAt") ?: 0L
                        if (System.currentTimeMillis() < expiresAt) {
                            setMembership(membership, expiresAt, shouldSync = false)
                        } else {
                            resetPremium()
                        }
                    } else {
                        resetPremium()
                    }
                } else {
                    // Document doesn't exist.
                    // Only reset if we are NOT currently processing a payment success.
                    if (pendingMembershipType == null) {
                        resetPremium()
                    }
                }
            }
    }

    fun syncPublicMembers() {
        startPublicMembersListener()
    }

    private fun startPublicMembersListener() {
        val db = FirebaseFirestore.getInstance()
        db.collection("public_members")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                val allMembers = snapshot.documents.mapNotNull { doc ->
                    try {
                        val member = doc.toObject(MemberInfo::class.java)
                        val actualType =
                            doc.getString("membershipType") ?: doc.getString("type")
                            ?: member?.membershipType
                            ?: "FREE"
                        member?.copy(membershipType = actualType)
                    } catch (ex: Exception) {
                        null
                    }
                }
                _silverMembers.value = allMembers.filter {
                    it.membershipType.equals(
                        "SILVER",
                        ignoreCase = true
                    ) || it.membershipType.equals("PATRON", ignoreCase = true)
                }.sortedByDescending { it.timestamp }
                _goldMembers.value = allMembers.filter {
                    it.membershipType.equals(
                        "GOLD",
                        ignoreCase = true
                    ) || it.membershipType.equals("LIVE", ignoreCase = true)
                }.sortedByDescending { it.timestamp }
            }
    }

    fun setMembership(type: MembershipType, expiresAt: Long = 0, shouldSync: Boolean = true) {
        _membershipType.value = type
        userPrefs.edit().putString("membership_type", type.name).apply()
        userPrefs.edit().putLong("membership_expiry", expiresAt).apply()
        userPrefs.edit().putBoolean("is_premium", type != MembershipType.FREE).apply()
        if (shouldSync && FirebaseAuth.getInstance().currentUser != null) syncToCloud()
    }

    fun resetPremium() {
        _membershipType.value = MembershipType.FREE
        userPrefs.edit().putString("membership_type", MembershipType.FREE.name).apply()
        userPrefs.edit().putLong("membership_expiry", 0L).apply()
        userPrefs.edit().putBoolean("is_premium", false).apply()
    }

    fun updateSelection(
        book: String,
        chapter: Int,
        highlightId: Int? = null,
        verseNumber: String? = null,
        addToHistory: Boolean = true
    ) {
        if (addToHistory && (currentBook.value != book || currentChapter.value != chapter)) {
            val last = NavHistoryItem(currentBook.value, currentChapter.value)
            if (backStack.isEmpty() || backStack.last() != last) {
                backStack.add(last)
                if (backStack.size > 50) backStack.removeAt(0)
                forwardStack.clear()
                saveHistory()
                updateHistoryStates()
            }
        }
        currentBook.value = book
        currentChapter.value = chapter
        highlightVerseId.value = highlightId
        highlightVerseNumber.value = verseNumber
    }

    fun goBack() {
        if (backStack.isNotEmpty()) {
            val current = NavHistoryItem(currentBook.value, currentChapter.value)
            forwardStack.add(current)
            val prev = backStack.removeAt(backStack.size - 1)
            currentBook.value = prev.book
            currentChapter.value = prev.chapter
            saveHistory()
            updateHistoryStates()
        }
    }

    fun goForward() {
        if (forwardStack.isNotEmpty()) {
            val current = NavHistoryItem(currentBook.value, currentChapter.value)
            backStack.add(current)
            val next = forwardStack.removeAt(forwardStack.size - 1)
            currentBook.value = next.book
            currentBook.value = next.book
            currentChapter.value = next.chapter
            saveHistory()
            updateHistoryStates()
        }
    }

    private fun updateHistoryStates() {
        _canGoBack.value = backStack.isNotEmpty()
        _canGoForward.value = forwardStack.isNotEmpty()
    }

    private fun saveHistory() {
        try {
            val backArray = JSONArray()
            backStack.forEach {
                val obj = JSONObject()
                obj.put("book", it.book)
                obj.put("chapter", it.chapter)
                backArray.put(obj)
            }

            val forwardArray = JSONArray()
            forwardStack.forEach {
                val obj = JSONObject()
                obj.put("book", it.book)
                obj.put("chapter", it.chapter)
                forwardArray.put(obj)
            }

            prefs.edit().apply {
                putString(KEY_BACK_STACK, backArray.toString())
                putString(KEY_FORWARD_STACK, forwardArray.toString())
                apply()
            }
        } catch (e: Exception) {
            Log.e("TransformViewModel", "Error saving history", e)
        }
    }

    private fun loadHistory() {
        try {
            val backStr = prefs.getString(KEY_BACK_STACK, null)
            if (!backStr.isNullOrEmpty()) {
                val array = JSONArray(backStr)
                backStack.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    backStack.add(NavHistoryItem(obj.getString("book"), obj.getInt("chapter")))
                }
            }

            val forwardStr = prefs.getString(KEY_FORWARD_STACK, null)
            if (!forwardStr.isNullOrEmpty()) {
                val array = JSONArray(forwardStr)
                forwardStack.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    forwardStack.add(NavHistoryItem(obj.getString("book"), obj.getInt("chapter")))
                }
            }
            updateHistoryStates()
        } catch (e: Exception) {
            Log.e("TransformViewModel", "Error loading history", e)
        }
    }

    fun clearHighlight() {
        highlightVerseId.value = null
        highlightVerseNumber.value = null
    }

    fun updateVersion(version: String) {
        currentVersion.value = version
    }

    fun updateSplitVersion(version: String) {
        splitVersion.value = version
    }

    fun setSplitMode(enabled: Boolean) {
        isSplitMode.value = enabled
    }

    fun setSplitOrientation(isVertical: Boolean) {
        _isVerticalSplit.value = isVertical; prefs.edit().putBoolean("vertical_split", isVertical)
            .apply()
    }

    fun updateFontSettings(settings: FontSettings) {
        fontSettings.value = settings; saveFontSettings(settings)
    }

    fun updateFontColor(colorHex: String) {
        val updated = fontSettings.value.copy(fontColor = colorHex)
        fontSettings.value = updated
        saveFontSettings(updated)
    }

    fun updateFontSize(newSize: Float) {
        val current = fontSettings.value
        val clampedSize = newSize.coerceIn(12f, 70f)
        if (abs(current.fontSize - clampedSize) > 0.1f) {
            val updated = current.copy(fontSize = clampedSize)
            fontSettings.value = updated
            saveFontSettings(updated)
        }
    }

    private fun saveFontSettings(settings: FontSettings) {
        ThemeHelper.setFontColor(getApplication(), settings.fontColor)
        prefs.edit().apply {
            putFloat("font_size", settings.fontSize)
            putString("font_family", settings.fontFamily)
            putBoolean("is_bold", settings.isBold)
            putBoolean("is_italic", settings.isItalic)
            putFloat("letter_spacing", settings.letterSpacing)
            putFloat("line_height", settings.lineHeight)
            putString("font_color", settings.fontColor)
            apply()
        }
    }

    private fun loadFontSettings(): FontSettings {
        val savedFamily = prefs.getString("font_family", "Serif") ?: "Serif"
        // Upgrade legacy "Times New Roman" setting which lacked Mizo ṭ/Ṭ glyphs
        val safeFamily = if (savedFamily == "Times New Roman") "Serif" else savedFamily
        val savedLineHeight = prefs.getFloat("line_height", 0.20f)
        val safeLineHeight = savedLineHeight.coerceIn(-0.50f, 2.50f)
        val effectiveColor = ThemeHelper.getFontColor(getApplication())

        return FontSettings(
            fontSize = prefs.getFloat("font_size", 18f),
            fontFamily = safeFamily,
            isBold = prefs.getBoolean("is_bold", false),
            isItalic = prefs.getBoolean("is_italic", false),
            letterSpacing = prefs.getFloat("letter_spacing", 0f),
            lineHeight = safeLineHeight,
            fontColor = effectiveColor
        )
    }

    fun refreshFontSettings() {
        fontSettings.value = loadFontSettings()
    }

    fun updateSidePadding(padding: Float) {
        sidePadding.value = padding; prefs.edit().putFloat("side_padding", padding).apply()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        keepScreenOn.value = enabled; prefs.edit().putBoolean("keep_screen_on", enabled).apply()
    }

    fun setEyeProtectionEnabled(enabled: Boolean) {
        isEyeProtectionEnabled.value = enabled
        prefs.edit().putBoolean("eye_protection_enabled", enabled).apply()
    }

    fun setCopyIncludeReference(enabled: Boolean) {
        copyIncludeReference.value = enabled
        prefs.edit().putBoolean("copy_include_ref", enabled).apply()
    }

    fun setCopyReferenceAtBottom(atBottom: Boolean) {
        copyReferenceAtBottom.value = atBottom
        prefs.edit().putBoolean("copy_ref_bottom", atBottom).apply()
    }

    fun setCopyHideVerseNumbers(hide: Boolean) {
        copyHideVerseNumbers.value = hide
        prefs.edit().putBoolean("copy_hide_verse_numbers", hide).apply()
    }

    fun resetAllData(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser
        viewModelScope.launch {
            repository.clearAllUserData()
            if (user != null) FirebaseFirestore.getInstance().collection("users").document(user.uid)
                .collection("backup").document("data").delete()
        }
    }

    // Cloud Sync Logic
    fun syncFromCloud() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (isDownloadingFromCloud) return
        isDownloadingFromCloud = true

        FirebaseFirestore.getInstance().collection("users").document(user.uid)
            .collection("backup").document("data")
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    viewModelScope.launch {
                        try {
                            val bookmarks = doc.get("bookmarks") as? List<Map<String, Any>>
                            if (bookmarks != null) {
                                repository.userDao.deleteAllBookmarks()
                                bookmarks.forEach { map ->
                                    val b = Bookmark(
                                        verseId = (map["verseId"] as? Number)?.toInt() ?: 0,
                                        book = map["book"] as? String ?: "",
                                        chapter = (map["chapter"] as? Number)?.toInt() ?: 0,
                                        verse = map["verse"] as? String ?: "0",
                                        text = map["text"] as? String ?: "",
                                        version = map["version"] as? String ?: "MzOV",
                                        title = map["title"] as? String ?: "",
                                        note = map["note"] as? String ?: "",
                                        color = map["color"] as? String ?: "#FFD740",
                                        timestamp = (map["timestamp"] as? Number)?.toLong()
                                            ?: System.currentTimeMillis()
                                    )
                                    if (b.verseId != 0) repository.insertBookmark(b)
                                }
                            }

                            val pins = doc.get("pins") as? List<Map<String, Any>>
                            if (pins != null) {
                                repository.userDao.deleteAllPins()
                                pins.forEach { map ->
                                    val p = Pin(
                                        verseId = (map["verseId"] as? Number)?.toInt() ?: 0,
                                        book = map["book"] as? String ?: "",
                                        chapter = (map["chapter"] as? Number)?.toInt() ?: 0,
                                        verse = map["verse"] as? String ?: "0",
                                        text = map["text"] as? String ?: "",
                                        color = map["color"] as? String ?: "#FFD740",
                                        version = map["version"] as? String ?: "MzOV",
                                        timestamp = (map["timestamp"] as? Number)?.toLong()
                                            ?: System.currentTimeMillis()
                                    )
                                    if (p.verseId != 0) repository.insertPin(p)
                                }
                            }

                            val notes = doc.get("notes") as? List<Map<String, Any>>
                            if (notes != null) {
                                repository.userDao.deleteAllNotes()
                                notes.forEach { map ->
                                    try {
                                        val n = Note(
                                            verseId = (map["verseId"] as? Number)?.toInt() ?: 0,
                                            book = map["book"] as? String ?: "",
                                            chapter = (map["chapter"] as? Number)?.toInt() ?: 0,
                                            verse = map["verse"] as? String ?: "0",
                                            text = map["text"] as? String ?: "",
                                            bibleText = map["bibleText"] as? String ?: "",
                                            color = map["color"] as? String ?: "#FF5252",
                                            version = map["version"] as? String ?: "MzOV",
                                            timestamp = (map["timestamp"] as? Number)?.toLong()
                                                ?: System.currentTimeMillis()
                                        )
                                        if (n.verseId != 0) repository.insertNote(n)
                                    } catch (e: Exception) {
                                    }
                                }
                            }

                            repository.userDao.standardizeBookmarkVersions()
                            repository.userDao.standardizePinVersions()
                            repository.userDao.standardizeNoteVersions()
                        } catch (e: Exception) {
                            Log.e("Sync", "Error parsing cloud data", e)
                        } finally {
                            isDownloadingFromCloud = false
                        }
                    }
                } else {
                    isDownloadingFromCloud = false
                }
            }
            .addOnFailureListener {
                isDownloadingFromCloud = false
            }
    }

    fun syncToCloud() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (isDownloadingFromCloud) return

        viewModelScope.launch {
            val bookmarks = repository.userDao.getAllBookmarksSync()
            val pins = repository.userDao.getAllPinsSync()
            val notes = repository.userDao.getAllNotesSync()

            val backupData = mapOf(
                "bookmarks" to bookmarks.map { it.toMap() },
                "pins" to pins.map { it.toMap() },
                "notes" to notes.map { it.toMap() },
                "lastSync" to System.currentTimeMillis()
            )
            FirebaseFirestore.getInstance().collection("users").document(user.uid)
                .collection("backup").document("data")
                .set(backupData, SetOptions.merge())
        }
    }

    private fun Bookmark.toMap() = mapOf(
        "verseId" to verseId,
        "book" to book,
        "chapter" to chapter,
        "verse" to verse,
        "text" to text,
        "version" to version,
        "title" to title,
        "note" to note,
        "color" to color,
        "timestamp" to timestamp
    )

    private fun Pin.toMap() = mapOf(
        "verseId" to verseId, "book" to book, "chapter" to chapter, "verse" to verse,
        "text" to text, "color" to color, "version" to version, "timestamp" to timestamp
    )

    private fun Note.toMap() = mapOf(
        "verseId" to verseId,
        "book" to book,
        "chapter" to chapter,
        "verse" to verse,
        "text" to text,
        "bibleText" to bibleText,
        "color" to color,
        "version" to version,
        "timestamp" to timestamp
    )

    fun toggleBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            if (membershipType.value == MembershipType.FREE) {
                val currentCount = allBookmarks.value?.size ?: 0
                if (currentCount >= 10) {
                    _uiEvent.emit(UiEvent.ShowUpgradeDialog)
                    return@launch
                }
            }
            repository.insertBookmark(bookmark); syncToCloud()
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark); syncToCloud()
        }
    }

    fun addPin(pin: Pin) {
        viewModelScope.launch {
            if (membershipType.value == MembershipType.FREE) {
                val currentCount = allPins.value?.size ?: 0
                if (currentCount >= 10) {
                    _uiEvent.emit(UiEvent.ShowUpgradeDialog)
                    return@launch
                }
            }
            repository.insertPin(pin); syncToCloud()
        }
    }

    fun deletePin(pin: Pin) {
        viewModelScope.launch {
            repository.deletePin(pin); syncToCloud()
        }
    }

    fun saveNote(note: Note) {
        viewModelScope.launch {
            if (membershipType.value == MembershipType.FREE) {
                val currentCount = allNotes.value?.size ?: 0
                if (currentCount >= 10) {
                    _uiEvent.emit(UiEvent.ShowUpgradeDialog)
                    return@launch
                }
            }
            repository.insertNote(note); syncToCloud()
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note); syncToCloud()
        }
    }

    fun logVerseRead() {
        viewModelScope.launch {
            repository.incrementVersesRead(
                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
                ).format(Date())
            )
        }
    }

    fun addReadingTime(seconds: Long) {
        viewModelScope.launch {
            repository.addReadingTime(
                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
                ).format(Date()), seconds
            )
        }
    }

    private fun getTranslatedBookName(book: String, version: String): String {
        val v = version.lowercase().trim()
        val isEnglish = listOf("kjv", "niv", "asv", "greek (grk)").contains(v)
        val isBangla = v == "banglaov" || v == "bangla"
        
        if (isBangla) {
            return when (book.trim()) {
                "Josua-I", "Josua" -> "Josua"
                "Ṭah hla", "Tah hla" -> "Ṭahhla"
                else -> book
            }
        }

        // Normalize input for searching
        val normInput = normalizeForMapping(book)
        
        // Find best match in our mapping
        val entry = bookMapping.entries.find { 
            normalizeForMapping(it.key) == normInput || normalizeForMapping(it.value) == normInput 
        }

        return if (entry != null) {
            if (isEnglish) entry.value else entry.key
        } else {
            book
        }
    }

    private fun normalizeForMapping(s: String): String {
        return s.trim().lowercase(Locale.ROOT)
            .replace("[áàâ]".toRegex(), "a").replace("[éèê]".toRegex(), "e")
            .replace("[íìî]".toRegex(), "i").replace("[óòô]".toRegex(), "o")
            .replace("[úùû]".toRegex(), "u").replace("ṭ", "t")
            .replace("[^a-z0-9]".toRegex(), "")
    }

    private val bookMapping = mapOf(
        "Genesis" to "Genesis",
        "Exodus" to "Exodus",
        "Leviticus" to "Leviticus",
        "Numbers" to "Numbers",
        "Deuteronomy" to "Deuteronomy",
        "Josua-I" to "Joshua",
        "Josua" to "Joshua",
        "Joshua" to "Joshua",
        "Roreltute" to "Judges",
        "Ruthi" to "Ruth",
        "I - Samuela" to "1 Samuel",
        "1 Samuel" to "1 Samuel",
        "II - Samuela" to "2 Samuel",
        "2 Samuel" to "2 Samuel",
        "I - Lalte" to "1 Kings",
        "1 Kings" to "1 Kings",
        "II - Lalte" to "2 Kings",
        "2 Kings" to "2 Kings",
        "I - Chronicles" to "1 Chronicles",
        "1 Chronicles" to "1 Chronicles",
        "II - Chronicles" to "2 Chronicles",
        "2 Chronicles" to "2 Chronicles",
        "EZRA" to "Ezra",
        "Nehemia" to "Nehemiah",
        "Estheri" to "Esther",
        "Joba" to "Job",
        "Sam" to "Psalms",
        "Thufingte" to "Proverbs",
        "Thuhriltu" to "Ecclesiastes",
        "Hla Thlan Khawmte" to "Song of Solomon",
        "Hla Thlankhawmte" to "Song of Solomon",
        "Isaia" to "Isaiah",
        "Jeremia" to "Jeremiah",
        "Ṭah hla" to "Lamentations",
        "Tah hla" to "Lamentations",
        "Ezekiela" to "Ezekiel",
        "Daniela" to "Daniel",
        "Hosea" to "Hosea",
        "Joela" to "Joel",
        "Amosa" to "Amos",
        "Obadia" to "Obadiah",
        "Jona" to "Jonah",
        "Mika" to "Micah",
        "Nahuma" to "Nahum",
        "Habakuka" to "Habakkuk",
        "Zephania" to "Zephaniah",
        "Hagaia" to "Haggai",
        "Zakaria" to "Zechariah",
        "Malakia" to "Malachi",
        "Matthaia" to "Matthew",
        "Marka" to "Mark",
        "Luka" to "Luke",
        "Johana" to "John",
        "Tirhkohte" to "Acts",
        "Rom" to "Romans",
        "1 Korinth" to "1 Corinthians",
        "2 Korinth" to "2 Corinthians",
        "Galatia" to "Galatians",
        "Ephesi" to "Ephesians",
        "Philippi" to "Philippians",
        "Kolossa" to "Colossians",
        "1 Thessalonika" to "1 Thessalonians",
        "2 Thessalonika" to "2 Thessalonians",
        "1 Timothea" to "1 Timothy",
        "2 Timothea" to "2 Timothy",
        "Tita" to "Titus",
        "Philemona" to "Philemon",
        "Hebrai" to "Hebrews",
        "Jakoba" to "James",
        "1 Petera" to "1 Peter",
        "2 Petera" to "2 Peter",
        "1 Johana" to "1 John",
        "2 Johana" to "2 John",
        "3 Johana" to "3 John",
        "Juda" to "Jude",
        "Thupuan" to "Revelation"
    )

    override fun onCleared() {
        premiumListener?.remove()
        super.onCleared()
    }
}

class TransformViewModelFactory(
    private val repository: BibleRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransformViewModel::class.java)) return TransformViewModel(
            repository,
            application
        ) as T
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
