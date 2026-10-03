package com.bdavidgm.notasrec.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.net.Uri
import com.bdavidgm.notasrec.NotasApplication
import com.bdavidgm.notasrec.R
import com.bdavidgm.notasrec.ui.detail.DetailScreen
import com.bdavidgm.notasrec.ui.detail.DetailViewModel
import com.bdavidgm.notasrec.ui.home.HomeScreen
import com.bdavidgm.notasrec.ui.home.HomeViewModel
import com.bdavidgm.notasrec.ui.reminders.RemindersScreen
import com.bdavidgm.notasrec.ui.reminders.RemindersViewModel
import com.bdavidgm.notasrec.ui.tagcloud.TagCloudScreen
import com.bdavidgm.notasrec.ui.tagcloud.TagCloudViewModel
import com.bdavidgm.notasrec.ui.theme.Celeste
import com.bdavidgm.notasrec.ui.theme.CelesteClaro
import com.bdavidgm.notasrec.ui.theme.NegroTexto
import kotlinx.coroutines.launch

private const val ROUTE_HOME = "home"
private const val ROUTE_TAG_CLOUD = "tagCloud"
private const val ROUTE_REMINDERS = "reminders"

@Composable
fun NotasNavHost(
    pendingOpenDocumentUri: Uri? = null,
    onOpenDocumentConsumed: (Uri) -> Unit = {},
    pendingOpenNoteId: Long? = null,
    onOpenNoteConsumed: (Long) -> Unit = {},
) {
    val navController = rememberNavController()
    val repository = (LocalContext.current.applicationContext as NotasApplication).repository
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val drawerGesturesEnabled =
        currentRoute == ROUTE_HOME ||
            currentRoute == ROUTE_TAG_CLOUD ||
            currentRoute == ROUTE_REMINDERS

    // Shared across Home ↔ TagCloud so "Ir" can apply filters on the same HomeViewModel.
    val activity = LocalActivity.current as ComponentActivity
    val homeViewModel: HomeViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = HomeViewModel.factory(repository),
    )

    LaunchedEffect(pendingOpenDocumentUri) {
        val uri = pendingOpenDocumentUri ?: return@LaunchedEffect
        homeViewModel.openDocumentAsNote(uri) { noteId ->
            navController.navigate("detail/$noteId") {
                launchSingleTop = true
            }
        }
        onOpenDocumentConsumed(uri)
    }

    LaunchedEffect(pendingOpenNoteId) {
        val noteId = pendingOpenNoteId ?: return@LaunchedEffect
        navController.navigate("detail/$noteId") {
            launchSingleTop = true
        }
        onOpenNoteConsumed(noteId)
    }

    fun openDrawer() {
        scope.launch { drawerState.open() }
    }

    fun navigateDrawer(route: String) {
        scope.launch { drawerState.close() }
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(ROUTE_HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                    color = NegroTexto,
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_notes)) },
                    selected = currentRoute == ROUTE_HOME,
                    onClick = { navigateDrawer(ROUTE_HOME) },
                    icon = {
                        Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null)
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Celeste,
                        unselectedContainerColor = CelesteClaro.copy(alpha = 0.35f),
                        selectedTextColor = NegroTexto,
                        unselectedTextColor = NegroTexto,
                        selectedIconColor = NegroTexto,
                        unselectedIconColor = NegroTexto,
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_tags)) },
                    selected = currentRoute == ROUTE_TAG_CLOUD,
                    onClick = { navigateDrawer(ROUTE_TAG_CLOUD) },
                    icon = {
                        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Celeste,
                        unselectedContainerColor = CelesteClaro.copy(alpha = 0.35f),
                        selectedTextColor = NegroTexto,
                        unselectedTextColor = NegroTexto,
                        selectedIconColor = NegroTexto,
                        unselectedIconColor = NegroTexto,
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_reminders)) },
                    selected = currentRoute == ROUTE_REMINDERS,
                    onClick = { navigateDrawer(ROUTE_REMINDERS) },
                    icon = {
                        Icon(Icons.Filled.Notifications, contentDescription = null)
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Celeste,
                        unselectedContainerColor = CelesteClaro.copy(alpha = 0.35f),
                        selectedTextColor = NegroTexto,
                        unselectedTextColor = NegroTexto,
                        selectedIconColor = NegroTexto,
                        unselectedIconColor = NegroTexto,
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onOpenNote = { id -> navController.navigate("detail/$id") },
                    onOpenDrawer = ::openDrawer,
                )
            }
            composable(ROUTE_TAG_CLOUD) {
                val tagCloudVm: TagCloudViewModel = viewModel(
                    factory = TagCloudViewModel.factory(repository),
                )
                TagCloudScreen(
                    viewModel = tagCloudVm,
                    navigationIcon = {
                        IconButton(onClick = ::openDrawer) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.cd_open_drawer),
                                tint = NegroTexto,
                            )
                        }
                    },
                    onGoToFilteredNotes = { selectedIds ->
                        homeViewModel.setSelectedTagIds(selectedIds)
                        navController.navigate(ROUTE_HOME) {
                            popUpTo(ROUTE_HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(ROUTE_REMINDERS) {
                val remindersVm: RemindersViewModel = viewModel(
                    factory = RemindersViewModel.factory(repository),
                )
                RemindersScreen(
                    viewModel = remindersVm,
                    navigationIcon = {
                        IconButton(onClick = ::openDrawer) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.cd_open_drawer),
                                tint = NegroTexto,
                            )
                        }
                    },
                    onOpenNote = { id -> navController.navigate("detail/$id") },
                )
            }
            composable(
                route = "detail/{noteId}",
                arguments = listOf(
                    navArgument("noteId") { type = NavType.LongType },
                ),
            ) { entry ->
                val noteId = entry.arguments!!.getLong("noteId")
                val vm: DetailViewModel = viewModel(
                    key = noteId.toString(),
                    factory = DetailViewModel.factory(noteId, repository),
                )
                DetailScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.navigateUp() },
                    onOpenNote = { id ->
                        navController.navigate("detail/$id")
                    },
                )
            }
        }
    }
}
