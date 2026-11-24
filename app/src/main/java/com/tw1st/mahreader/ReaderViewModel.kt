package com.tw1st.mahreader

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tw1st.mahreader.data.AppDatabase
import com.tw1st.mahreader.data.BookEntity
import com.tw1st.mahreader.data.ReadingTheme
import com.tw1st.mahreader.pdf.PdfEngine
import com.tw1st.mahreader.tts.AutoReaderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FolderPdfItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).bookDao()
    val pdfEngine = PdfEngine(application)
    val autoReader = AutoReaderManager(application)

    private val prefs = application.getSharedPreferences("mah_reader_prefs", Context.MODE_PRIVATE)

    val allBooks: StateFlow<List<BookEntity>> = dao.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unfinishedBook: StateFlow<BookEntity?> = allBooks.map { books ->
        books.firstOrNull { it.totalPages > 0 && (it.lastReadPage + 1) < it.totalPages }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentBook = MutableStateFlow<BookEntity?>(null)
    val currentBook: StateFlow<BookEntity?> = _currentBook.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _readingTheme = MutableStateFlow(ReadingTheme.AMOLED_DARK)
    val readingTheme: StateFlow<ReadingTheme> = _readingTheme.asStateFlow()

    private val _isContinuousScroll = MutableStateFlow(true)
    val isContinuousScroll: StateFlow<Boolean> = _isContinuousScroll.asStateFlow()

    private val _isHudVisible = MutableStateFlow(false)
    val isHudVisible: StateFlow<Boolean> = _isHudVisible.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Auto-Reader State
    private val _isAutoReaderEnabled = MutableStateFlow(false)
    val isAutoReaderEnabled: StateFlow<Boolean> = _isAutoReaderEnabled.asStateFlow()

    val isAutoReaderPlaying: StateFlow<Boolean> = autoReader.isPlaying
    val autoReaderSpeed: StateFlow<Float> = autoReader.speechRate
    val isTtsAvailable: StateFlow<Boolean> = autoReader.isTtsAvailable
    val isMissingVoiceData: StateFlow<Boolean> = autoReader.isMissingVoiceData

    // Remembered folder of Light Novels
    private val _lastFolderName = MutableStateFlow(prefs.getString("folder_name", null))
    val lastFolderName: StateFlow<String?> = _lastFolderName.asStateFlow()

    private val _lastFolderUri = MutableStateFlow(prefs.getString("folder_uri", null))
    val lastFolderUri: StateFlow<String?> = _lastFolderUri.asStateFlow()

    private val _folderPdfs = MutableStateFlow<List<FolderPdfItem>>(emptyList())
    val folderPdfs: StateFlow<List<FolderPdfItem>> = _folderPdfs.asStateFlow()

    init {
        loadSavedFolder()
    }

    private fun loadSavedFolder() {
        val uriStr = prefs.getString("folder_uri", null) ?: return
        viewModelScope.launch {
            loadPdfsFromFolderUri(Uri.parse(uriStr))
        }
    }

    fun setNovelFolder(uri: Uri, folderName: String) {
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            prefs.edit()
                .putString("folder_uri", uri.toString())
                .putString("folder_name", folderName)
                .apply()

            _lastFolderUri.value = uri.toString()
            _lastFolderName.value = folderName
            loadPdfsFromFolderUri(uri)
        }
    }

    private suspend fun loadPdfsFromFolderUri(folderUri: Uri) = withContext(Dispatchers.IO) {
        try {
            val root = DocumentFile.fromTreeUri(getApplication(), folderUri) ?: return@withContext
            val files = root.listFiles()
            val pdfs = files.filter { it.isFile && (it.name?.endsWith(".pdf", ignoreCase = true) == true) }
                .map { doc ->
                    FolderPdfItem(
                        uri = doc.uri,
                        name = doc.name ?: "Unknown.pdf",
                        sizeBytes = doc.length()
                    )
                }
                .sortedBy { it.name.lowercase() }

            _folderPdfs.value = pdfs
        } catch (e: Exception) {
            android.util.Log.e("ReaderViewModel", "Error scanning folder PDFs", e)
        }
    }

    fun openBook(uri: Uri, title: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val pages = pdfEngine.openUri(uri)
                _totalPages.value = pages

                val existing = dao.getBookByUri(uri.toString())
                val initialPage = existing?.lastReadPage?.coerceIn(0, maxOf(0, pages - 1)) ?: 0
                val savedTheme = existing?.themeName?.let {
                    try { ReadingTheme.valueOf(it) } catch (_: Exception) { ReadingTheme.AMOLED_DARK }
                } ?: ReadingTheme.AMOLED_DARK
                val savedScroll = existing?.isContinuousScroll ?: true

                _currentPage.value = initialPage
                _readingTheme.value = savedTheme
                _isContinuousScroll.value = savedScroll

                val bookToSave = (existing ?: BookEntity(
                    uriString = uri.toString(),
                    title = title,
                    totalPages = pages,
                    lastReadPage = initialPage,
                    themeName = savedTheme.name,
                    isContinuousScroll = savedScroll
                )).copy(
                    totalPages = pages,
                    lastReadPage = initialPage,
                    lastReadTimestamp = System.currentTimeMillis()
                )

                val id = dao.insertOrUpdate(bookToSave)
                _currentBook.value = bookToSave.copy(id = if (existing != null) existing.id else id.toInt())
                _isHudVisible.value = false
            } catch (e: Exception) {
                android.util.Log.e("ReaderViewModel", "Error opening PDF", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onPageChanged(page: Int) {
        if (page != _currentPage.value) {
            _currentPage.value = page
            val book = _currentBook.value ?: return
            viewModelScope.launch(Dispatchers.IO) {
                dao.updateProgress(book.id, page)
                _currentBook.value = book.copy(lastReadPage = page)
            }
            if (_isAutoReaderEnabled.value && autoReader.isPlaying.value) {
                readCurrentPageAloud()
            }
        }
    }

    fun setReadingTheme(theme: ReadingTheme) {
        _readingTheme.value = theme
        pdfEngine.clearCache()
        val book = _currentBook.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateTheme(book.id, theme.name)
        }
    }

    fun setScrollMode(isContinuous: Boolean) {
        _isContinuousScroll.value = isContinuous
        val book = _currentBook.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateScrollMode(book.id, isContinuous)
        }
    }

    fun toggleHud() {
        _isHudVisible.value = !_isHudVisible.value
    }

    fun hideHud() {
        _isHudVisible.value = false
    }

    // Auto-Reader Controls
    fun toggleAutoReader() {
        val newState = !_isAutoReaderEnabled.value
        _isAutoReaderEnabled.value = newState
        if (newState) {
            readCurrentPageAloud()
        } else {
            autoReader.stop()
        }
    }

    fun pauseAutoReader() {
        autoReader.pause()
    }

    fun resumeAutoReader() {
        if (autoReader.isPlaying.value) {
            autoReader.pause()
        } else {
            readCurrentPageAloud()
        }
    }

    fun setAutoReaderSpeed(speed: Float) {
        autoReader.setSpeed(speed)
    }

    fun nextAutoReaderPage() {
        if (_currentPage.value + 1 < _totalPages.value) {
            onPageChanged(_currentPage.value + 1)
        } else {
            autoReader.stop()
        }
    }

    fun prevAutoReaderPage() {
        if (_currentPage.value > 0) {
            onPageChanged(_currentPage.value - 1)
        }
    }

    private fun readCurrentPageAloud() {
        viewModelScope.launch {
            val pageIndex = _currentPage.value
            val text = pdfEngine.extractPageText(pageIndex)
            autoReader.startReadingPage(text) {
                // When this page completes, automatically advance to next page!
                if (_currentPage.value + 1 < _totalPages.value) {
                    onPageChanged(_currentPage.value + 1)
                } else {
                    autoReader.stop()
                    _isAutoReaderEnabled.value = false
                }
            }
        }
    }

    fun closeBook() {
        autoReader.stop()
        _isAutoReaderEnabled.value = false
        pdfEngine.close()
        _currentBook.value = null
        _currentPage.value = 0
        _totalPages.value = 0
        _isHudVisible.value = false
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteBook(book)
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoReader.shutdown()
        pdfEngine.close()
    }
}
