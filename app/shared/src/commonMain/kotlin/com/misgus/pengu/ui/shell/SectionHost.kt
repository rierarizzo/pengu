package com.misgus.pengu.ui.shell

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.misgus.pengu.core.player.PlaybackTarget
import com.misgus.pengu.ui.discover.DiscoverViewModel
import com.misgus.pengu.ui.home.HomeScreen
import com.misgus.pengu.ui.home.HomeViewModel
import com.misgus.pengu.ui.library.LibraryScreen
import com.misgus.pengu.ui.library.LibraryViewModel
import com.misgus.pengu.ui.screens.SettingsScreen
import com.misgus.pengu.ui.search.SearchScreen
import com.misgus.pengu.ui.search.SearchViewModel
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.streams.StreamsRequest

/**
 * Content of the shell: shows [selected].
 *
 * Section ViewModels live in the root store, so their loaded data survives
 * switching sections; a section not yet visited never fires its own requests
 * because the load runs from the screen's `LaunchedEffect`.
 */
@Composable
fun SectionHost(
    selected: AppSection,
    navigator: ContentNavigator,
    onOpenStreams: (StreamsRequest) -> Unit,
    onOpenPlayer: (PlaybackTarget) -> Unit,
) {
    val services = LocalAppServices.current
    when (selected) {
        AppSection.HOME -> {
            val viewModel: HomeViewModel =
                viewModel {
                    HomeViewModel(
                        account = services.account,
                        catalogs = services.catalogs,
                        library = services.library,
                        progress = services.progress,
                        badges = services.watchedBadges,
                        tmdb = services.tmdb,
                        addons = services.addons,
                        settings = services.settings,
                        playbackUpdates = services.playbackUpdates,
                    )
                }
            HomeScreen(viewModel, navigator, onOpenStreams)
        }
        AppSection.LIBRARY -> {
            val viewModel: LibraryViewModel =
                viewModel {
                    LibraryViewModel(
                        account = services.account,
                        library = services.library,
                        progress = services.progress,
                        badges = services.watchedBadges,
                    )
                }
            LibraryScreen(viewModel, navigator)
        }
        AppSection.SEARCH -> {
            val viewModel: SearchViewModel =
                viewModel {
                    SearchViewModel(
                        account = services.account,
                        catalogs = services.catalogs,
                    )
                }
            // The discover feed is embedded in Search, so both ViewModels live
            // in the same section scope.
            val discoverViewModel: DiscoverViewModel =
                viewModel {
                    DiscoverViewModel(
                        account = services.account,
                        catalogs = services.catalogs,
                    )
                }
            SearchScreen(
                viewModel = viewModel,
                discoverViewModel = discoverViewModel,
                navigator = navigator,
            )
        }
        AppSection.SETTINGS ->
            SettingsScreen(
                onOpenPlayer = onOpenPlayer,
                onOpenCategory = { category -> navigator.push(ContentRoute.SettingsPage(category)) },
            )
    }
}
