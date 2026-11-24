@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.tw1st.mahreader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tw1st.mahreader.data.BookEntity
import com.tw1st.mahreader.data.ReadingTheme
import com.tw1st.mahreader.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        intent?.data?.let { uri ->
            val title = queryFileName(this, uri) ?: "Light Novel"
            viewModel.openBook(uri, title)
        }

        setContent {
            MahReaderTheme {
                val currentBook by viewModel.currentBook.collectAsState()
                val isHudVisible by viewModel.isHudVisible.collectAsState()

                LaunchedEffect(currentBook, isHudVisible) {
                    if (currentBook != null && !isHudVisible) {
                        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                    }
                }

                if (currentBook == null) {
                    BookshelfScreen(
                        viewModel = viewModel,
                        onOpenUri = { uri, name -> viewModel.openBook(uri, name) }
                    )
                } else {
                    ReaderScreen(
                        viewModel = viewModel,
                        book = currentBook!!,
                        onClose = { viewModel.closeBook() }
                    )
                }
            }
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }
}

@Composable
fun BookshelfScreen(
    viewModel: ReaderViewModel,
    onOpenUri: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val allBooks by viewModel.allBooks.collectAsState()
    val unfinishedBook by viewModel.unfinishedBook.collectAsState()
    val lastFolderName by viewModel.lastFolderName.collectAsState()
    val folderPdfs by viewModel.folderPdfs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val folderDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
            val name = folderDoc?.name ?: "Novels Folder"
            viewModel.setNovelFolder(treeUri, name)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            var fileName = "Light Novel.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            onOpenUri(uri, fileName)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Black,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "- Mah Reader -",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = CrimsonRed
                        )
                        Text(
                            text = "Light Novel & PDF True Dark Viewer",
                            fontSize = 12.sp,
                            color = TextGray
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { filePicker.launch(arrayOf("application/pdf")) },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardDarkSecondary)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = "Open PDF", tint = TextWhite)
                        }

                        Button(
                            onClick = { folderPicker.launch(null) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (lastFolderName != null) "Folder" else "Set Folder",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    onClick = { folderPicker.launch(null) },
                    shape = RoundedCornerShape(10.dp),
                    color = CardDark,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = CrimsonRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (lastFolderName != null) "📁 $lastFolderName" else "Tap to choose Light Novel folder",
                                color = if (lastFolderName != null) TextWhite else TextGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = if (lastFolderName != null) "Change" else "Browse",
                            color = CrimsonRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            if (unfinishedBook != null) {
                val book = unfinishedBook!!
                ExtendedFloatingActionButton(
                    onClick = {
                        onOpenUri(Uri.parse(book.uriString), book.title)
                    },
                    containerColor = CrimsonRed,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    icon = {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume Reading")
                    },
                    text = {
                        Column {
                            Text(
                                text = "Resume Reading",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${book.title.take(18)}... (p. ${book.lastReadPage + 1})",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CrimsonRed)
                }
            } else if (allBooks.isEmpty() && folderPdfs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = CrimsonRed.copy(alpha = 0.6f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No light novels found",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap 'Set Folder' to link your Light Novels folder, or 'Open PDF' to select a single novel.",
                        color = TextGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (allBooks.isNotEmpty()) {
                        item {
                            Text(
                                text = "RECENT NOVELS",
                                color = TextGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(allBooks, key = { it.id }) { book ->
                            BookCard(
                                book = book,
                                onClick = {
                                    onOpenUri(Uri.parse(book.uriString), book.title)
                                },
                                onDelete = {
                                    viewModel.deleteBook(book)
                                }
                            )
                        }
                    }

                    if (folderPdfs.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "FROM FOLDER (${folderPdfs.size} NOVELS)",
                                color = TextGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(folderPdfs, key = { it.uri.toString() }) { item ->
                            FolderPdfCard(
                                item = item,
                                onClick = {
                                    onOpenUri(item.uri, item.name)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderPdfCard(
    item: FolderPdfItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = CardDark,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardDarkSecondary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = CrimsonRed,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val sizeMb = String.format(Locale.US, "%.1f MB", item.sizeBytes / (1024f * 1024f))
                Text(
                    text = sizeMb,
                    color = TextGray,
                    fontSize = 11.sp
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun BookCard(
    book: BookEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = book.progressPercentage()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = CardDark,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardDarkSecondary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = CrimsonRed,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Page ${book.lastReadPage + 1} of ${book.totalPages}",
                        color = TextGray,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "• $progress%",
                        color = if (progress >= 100) AccentGreen else CrimsonRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = CrimsonRed,
                    trackColor = Color(0xFF222226),
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextGray.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    book: BookEntity,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val currentPage by viewModel.currentPage.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val readingTheme by viewModel.readingTheme.collectAsState()
    val isContinuousScroll by viewModel.isContinuousScroll.collectAsState()
    val isHudVisible by viewModel.isHudVisible.collectAsState()

    // Auto-Reader states
    val isAutoReaderEnabled by viewModel.isAutoReaderEnabled.collectAsState()
    val isAutoReaderPlaying by viewModel.isAutoReaderPlaying.collectAsState()
    val autoReaderSpeed by viewModel.autoReaderSpeed.collectAsState()
    val isMissingVoiceData by viewModel.isMissingVoiceData.collectAsState()

    var showTtsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isMissingVoiceData) {
        if (isMissingVoiceData) {
            showTtsDialog = true
        }
    }

    if (showTtsDialog) {
        AlertDialog(
            onDismissRequest = { showTtsDialog = false },
            containerColor = CardDark,
            title = { Text("Download Offline Voice", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Your device has a speech engine but needs the offline voice data downloaded.", color = TextGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTtsDialog = false
                        try {
                            context.startActivity(viewModel.autoReader.getInstallTtsDataIntent())
                        } catch (_: Exception) {}
                    }
                ) {
                    Text("Open Settings", color = CrimsonRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTtsDialog = false }) {
                    Text("Cancel", color = TextGray)
                }
            }
        )
    }

    val screenBg = when (readingTheme) {
        ReadingTheme.AMOLED_DARK -> Black
        ReadingTheme.EYE_STRAIN_AMBER -> EyeStrainBg
        ReadingTheme.SOFT_CHARCOAL -> Color(0xFF161619)
        ReadingTheme.ORIGINAL_LIGHT -> Color(0xFFF5F5F7)
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { viewModel.toggleHud() }
                )
            }
    ) {
        if (isContinuousScroll) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)

            LaunchedEffect(listState.firstVisibleItemIndex) {
                viewModel.onPageChanged(listState.firstVisibleItemIndex)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(totalPages) { pageIndex ->
                    PdfPageView(
                        viewModel = viewModel,
                        pageIndex = pageIndex,
                        targetWidth = screenWidthPx,
                        theme = readingTheme
                    )
                }
            }
        } else {
            val pagerState = rememberPagerState(
                initialPage = currentPage,
                pageCount = { totalPages }
            )

            LaunchedEffect(pagerState.currentPage) {
                viewModel.onPageChanged(pagerState.currentPage)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                PdfPageView(
                    viewModel = viewModel,
                    pageIndex = pageIndex,
                    targetWidth = screenWidthPx,
                    theme = readingTheme
                )
            }
        }

        // Top HUD Bar
        AnimatedVisibility(
            visible = isHudVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                title = book.title,
                currentPage = currentPage,
                totalPages = totalPages,
                isAutoReaderEnabled = isAutoReaderEnabled,
                onToggleAutoReader = { viewModel.toggleAutoReader() },
                onBack = onClose
            )
        }

        // Bottom Settings Controls
        AnimatedVisibility(
            visible = isHudVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomControls(
                currentPage = currentPage,
                totalPages = totalPages,
                currentTheme = readingTheme,
                isContinuous = isContinuousScroll,
                onPageChange = { targetPage -> viewModel.onPageChanged(targetPage) },
                onThemeChange = { theme -> viewModel.setReadingTheme(theme) },
                onToggleScroll = { viewModel.setScrollMode(!isContinuousScroll) }
            )
        }

        // Floating Auto-Reader Controller (visible whenever Auto-Reader is ON)
        AnimatedVisibility(
            visible = isAutoReaderEnabled,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isHudVisible) 140.dp else 24.dp)
        ) {
            AutoReaderFloatingBar(
                currentPage = currentPage,
                totalPages = totalPages,
                isPlaying = isAutoReaderPlaying,
                currentSpeed = autoReaderSpeed,
                onTogglePlay = { viewModel.resumeAutoReader() },
                onPrevPage = { viewModel.prevAutoReaderPage() },
                onNextPage = { viewModel.nextAutoReaderPage() },
                onSpeedChange = { speed -> viewModel.setAutoReaderSpeed(speed) },
                onClose = { viewModel.toggleAutoReader() }
            )
        }
    }
}

@Composable
fun AutoReaderFloatingBar(
    currentPage: Int,
    totalPages: Int,
    isPlaying: Boolean,
    currentSpeed: Float,
    onTogglePlay: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF2121217),
        border = BorderStroke(1.dp, CrimsonBorder),
        shadowElevation = 8.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Header status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) Color(0xFF30D158) else Color(0xFFFF9500))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPlaying) "Auto-Reading p. ${currentPage + 1}" else "Paused (p. ${currentPage + 1})",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Close / Turn Off Button
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Turn off Auto-Reader", tint = TextGray, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Playback controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Prev page
                IconButton(onClick = onPrevPage, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Page", tint = TextWhite)
                }

                // Play / Pause Circle Button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CrimsonRed)
                        .clickable { onTogglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Next page
                IconButton(onClick = onNextPage, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next Page", tint = TextWhite)
                }

                // Speed Selector Pills
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1.0f to "1x", 1.25f to "1.25x", 1.5f to "1.5x", 2.0f to "2x").forEach { (speed, label) ->
                        val isSelected = currentSpeed == speed
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) CrimsonRed else Color(0xFF222228))
                                .clickable { onSpeedChange(speed) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextGray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPageView(
    viewModel: ReaderViewModel,
    pageIndex: Int,
    targetWidth: Int,
    theme: ReadingTheme
) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, pageIndex, theme) {
        value = try {
            viewModel.pdfEngine.renderPage(pageIndex, targetWidth, theme)
        } catch (e: Exception) {
            null
        }
    }

    val bitmap = bitmapState.value
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CrimsonRed, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun ReaderTopBar(
    title: String,
    currentPage: Int,
    totalPages: Int,
    isAutoReaderEnabled: Boolean,
    onToggleAutoReader: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xEE0D0D10),
        border = BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, bottom = 10.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextWhite
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Page ${currentPage + 1} of $totalPages",
                    color = TextGray,
                    fontSize = 11.sp
                )
            }

            // Auto-Reader Toggle Button
            Surface(
                onClick = onToggleAutoReader,
                shape = RoundedCornerShape(10.dp),
                color = if (isAutoReaderEnabled) CrimsonRed else Color(0xFF222228),
                border = BorderStroke(1.dp, if (isAutoReaderEnabled) CrimsonBorder else CardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = "Auto Reader",
                        tint = if (isAutoReaderEnabled) Color.White else TextLightGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isAutoReaderEnabled) "Reading" else "Listen",
                        color = if (isAutoReaderEnabled) Color.White else TextLightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ReaderBottomControls(
    currentPage: Int,
    totalPages: Int,
    currentTheme: ReadingTheme,
    isContinuous: Boolean,
    onPageChange: (Int) -> Unit,
    onThemeChange: (ReadingTheme) -> Unit,
    onToggleScroll: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = Color(0xF5101014),
        border = BorderStroke(1.dp, Color(0x22FFFFFF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (totalPages > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentPage + 1}",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(36.dp)
                    )

                    Slider(
                        value = currentPage.toFloat(),
                        onValueChange = { onPageChange(it.toInt()) },
                        valueRange = 0f..(totalPages - 1).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = CrimsonRed,
                            activeTrackColor = CrimsonRed,
                            inactiveTrackColor = Color(0xFF282830)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "$totalPages",
                        color = TextGray,
                        fontSize = 12.sp,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val themes = listOf(
                        ReadingTheme.AMOLED_DARK to "AMOLED",
                        ReadingTheme.EYE_STRAIN_AMBER to "Eye Strain",
                        ReadingTheme.SOFT_CHARCOAL to "Charcoal",
                        ReadingTheme.ORIGINAL_LIGHT to "Light"
                    )

                    themes.forEach { (theme, label) ->
                        val isSelected = currentTheme == theme
                        val chipBg = when {
                            isSelected && theme == ReadingTheme.EYE_STRAIN_AMBER -> EyeStrainAmber
                            isSelected -> CrimsonRed
                            else -> Color(0xFF202026)
                        }
                        val chipText = when {
                            isSelected && theme == ReadingTheme.EYE_STRAIN_AMBER -> Color.Black
                            isSelected -> Color.White
                            else -> TextGray
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(chipBg)
                                .clickable { onThemeChange(theme) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = chipText
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF202026))
                        .clickable { onToggleScroll() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isContinuous) "Scroll ↕" else "Flip ↔",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }
            }
        }
    }
}
