package com.qbitcore.booklip.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qbitcore.booklip.data.BookRepository
import com.qbitcore.booklip.settings.SettingsRepository
import com.qbitcore.booklip.ui.library.LibraryScreen
import com.qbitcore.booklip.ui.library.LibraryViewModel
import com.qbitcore.booklip.ui.library.LibraryViewModelFactory
import com.qbitcore.booklip.ui.reader.ReaderScreen
import com.qbitcore.booklip.ui.reader.ReaderViewModel
import com.qbitcore.booklip.ui.reader.ReaderViewModelFactory

private object Routes {
    const val LIBRARY = "library"
    const val READER = "reader/{bookId}"
    fun reader(bookId: String) = "reader/$bookId"
}

@Composable
fun BooklipNavHost(repository: BookRepository, settingsRepository: SettingsRepository) {
    val navController = rememberNavController()
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModelFactory(repository))

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                viewModel = libraryViewModel,
                repository = repository,
                onOpenBook = { book -> navController.navigate(Routes.reader(book.id)) },
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            val readerViewModel: ReaderViewModel = viewModel(
                factory = ReaderViewModelFactory(repository, settingsRepository, bookId),
            )
            ReaderScreen(viewModel = readerViewModel, onClose = { navController.popBackStack() })
        }
    }
}
