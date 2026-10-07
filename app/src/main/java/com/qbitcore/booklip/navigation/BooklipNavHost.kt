package com.qbitcore.booklip.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qbitcore.booklip.BooklipApplication
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.BookFormat
import com.qbitcore.booklip.ui.cloud.CloudConnectScreen
import com.qbitcore.booklip.ui.cloud.CloudFileBrowserScreen
import com.qbitcore.booklip.ui.cloud.CloudViewModel
import com.qbitcore.booklip.ui.library.FolderDetailScreen
import com.qbitcore.booklip.ui.library.LibraryScreen
import com.qbitcore.booklip.ui.library.LibraryViewModel
import com.qbitcore.booklip.ui.library.StatsScreen
import com.qbitcore.booklip.ui.reader.PdfReaderScreen
import com.qbitcore.booklip.ui.reader.PdfReaderViewModel
import com.qbitcore.booklip.ui.reader.ReaderScreen
import com.qbitcore.booklip.ui.reader.ReaderViewModel

private object Routes {
    const val LIBRARY = "library"
    const val UNFILED = "unfiled"
    const val FOLDER = "folder/{folderId}"
    const val STATS = "stats"
    const val CLOUD_CONNECT = "cloud"
    const val CLOUD_BROWSE = "cloud/browse"
    const val READER = "reader/{bookId}"
    const val PDF_READER = "pdfreader/{bookId}"
    fun folder(folderId: String) = "folder/$folderId"
    fun reader(book: Book) = if (book.format == BookFormat.PDF) "pdfreader/${book.id}" else "reader/${book.id}"
}

@Composable
fun BooklipNavHost() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as BooklipApplication
    // Activity-scoped: the library, its folder screens and the cloud screens share them.
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(app))
    val cloudViewModel: CloudViewModel = viewModel(factory = CloudViewModel.Factory(app))
    val openBook: (Book) -> Unit = { book -> navController.navigate(Routes.reader(book)) { launchSingleTop = true } }
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                vm = libraryViewModel,
                onOpenBook = openBook,
                onOpenFolder = { folderId -> navController.navigate(if (folderId == null) Routes.UNFILED else Routes.folder(folderId)) },
                onOpenStats = { navController.navigate(Routes.STATS) },
                onOpenCloud = { navController.navigate(Routes.CLOUD_CONNECT) },
            )
        }
        composable(Routes.UNFILED) {
            FolderDetailScreen(libraryViewModel, folderId = null, onBack = back, onOpenBook = openBook)
        }
        composable(Routes.FOLDER, arguments = listOf(navArgument("folderId") { type = NavType.StringType })) { entry ->
            val folderId = entry.arguments?.getString("folderId") ?: return@composable
            FolderDetailScreen(libraryViewModel, folderId = folderId, onBack = back, onOpenBook = openBook)
        }
        composable(Routes.STATS) {
            val libraryState by libraryViewModel.uiState.collectAsState()
            StatsScreen(statsRepository = app.statsRepository, books = libraryState.books, onClose = back)
        }
        composable(Routes.CLOUD_CONNECT) {
            CloudConnectScreen(
                viewModel = cloudViewModel,
                onClose = back,
                onBrowse = { provider ->
                    cloudViewModel.openProvider(provider)
                    navController.navigate(Routes.CLOUD_BROWSE)
                },
            )
        }
        composable(Routes.CLOUD_BROWSE) {
            CloudFileBrowserScreen(
                viewModel = cloudViewModel,
                onExit = back,
                onImported = { navController.popBackStack(Routes.LIBRARY, inclusive = false) },
            )
        }
        composable(Routes.READER, arguments = listOf(navArgument("bookId") { type = NavType.StringType })) { entry ->
            val bookId = entry.arguments?.getString("bookId") ?: return@composable
            // Scoped to this back-stack entry: closing the book clears the view model (and stops speech).
            val vm: ReaderViewModel = viewModel(factory = ReaderViewModel.Factory(app, bookId))
            ReaderScreen(vm, onClose = back)
        }
        composable(Routes.PDF_READER, arguments = listOf(navArgument("bookId") { type = NavType.StringType })) { entry ->
            val bookId = entry.arguments?.getString("bookId") ?: return@composable
            val vm: PdfReaderViewModel = viewModel(factory = PdfReaderViewModel.Factory(app, bookId))
            PdfReaderScreen(vm, onClose = back)
        }
    }
}
