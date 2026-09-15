package com.qbitcore.booklip.navigation

import android.app.Application
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
import com.qbitcore.booklip.data.BookRepository
import com.qbitcore.booklip.data.ReadingStatsRepository
import com.qbitcore.booklip.data.cloud.CloudRepository
import com.qbitcore.booklip.model.BookFormat
import com.qbitcore.booklip.settings.SettingsRepository
import com.qbitcore.booklip.ui.cloud.CloudConnectScreen
import com.qbitcore.booklip.ui.cloud.CloudFileBrowserScreen
import com.qbitcore.booklip.ui.cloud.CloudViewModel
import com.qbitcore.booklip.ui.cloud.CloudViewModelFactory
import com.qbitcore.booklip.ui.library.LibraryScreen
import com.qbitcore.booklip.ui.library.LibraryViewModel
import com.qbitcore.booklip.ui.library.LibraryViewModelFactory
import com.qbitcore.booklip.ui.library.StatsScreen
import com.qbitcore.booklip.ui.reader.PdfReaderScreen
import com.qbitcore.booklip.ui.reader.PdfReaderViewModel
import com.qbitcore.booklip.ui.reader.PdfReaderViewModelFactory
import com.qbitcore.booklip.ui.reader.ReaderScreen
import com.qbitcore.booklip.ui.reader.ReaderViewModel
import com.qbitcore.booklip.ui.reader.ReaderViewModelFactory

private object Routes {
    const val LIBRARY = "library"
    const val STATS = "stats"
    const val CLOUD_CONNECT = "cloud"
    const val CLOUD_BROWSE = "cloud/browse"
    const val READER = "reader/{bookId}"
    const val PDF_READER = "pdfreader/{bookId}"
    fun reader(bookId: String) = "reader/$bookId"
    fun pdfReader(bookId: String) = "pdfreader/$bookId"
}

@Composable
fun BooklipNavHost(
    repository: BookRepository,
    settingsRepository: SettingsRepository,
    statsRepository: ReadingStatsRepository,
    cloudRepository: CloudRepository,
) {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as Application
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModelFactory(repository))
    val cloudViewModel: CloudViewModel = viewModel(factory = CloudViewModelFactory(cloudRepository))

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                viewModel = libraryViewModel,
                repository = repository,
                onOpenBook = { book ->
                    val route = if (book.format == BookFormat.PDF) Routes.pdfReader(book.id) else Routes.reader(book.id)
                    navController.navigate(route)
                },
                onOpenStats = { navController.navigate(Routes.STATS) },
                onOpenCloud = { navController.navigate(Routes.CLOUD_CONNECT) },
            )
        }
        composable(Routes.STATS) {
            val libraryState by libraryViewModel.uiState.collectAsState()
            StatsScreen(
                statsRepository = statsRepository,
                books = libraryState.books,
                onClose = { navController.popBackStack() },
            )
        }
        composable(Routes.CLOUD_CONNECT) {
            CloudConnectScreen(
                viewModel = cloudViewModel,
                onClose = { navController.popBackStack() },
                onBrowse = {
                    cloudViewModel.openProvider(it)
                    navController.navigate(Routes.CLOUD_BROWSE)
                },
            )
        }
        composable(Routes.CLOUD_BROWSE) {
            CloudFileBrowserScreen(
                viewModel = cloudViewModel,
                onExit = { navController.popBackStack() },
                onImported = { navController.popBackStack(Routes.LIBRARY, inclusive = false) },
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            val readerViewModel: ReaderViewModel = viewModel(
                factory = ReaderViewModelFactory(application, repository, settingsRepository, statsRepository, bookId),
            )
            ReaderScreen(viewModel = readerViewModel, onClose = { navController.popBackStack() })
        }
        composable(
            route = Routes.PDF_READER,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            val pdfViewModel: PdfReaderViewModel = viewModel(
                factory = PdfReaderViewModelFactory(application, repository, bookId),
            )
            PdfReaderScreen(viewModel = pdfViewModel, onClose = { navController.popBackStack() })
        }
    }
}
