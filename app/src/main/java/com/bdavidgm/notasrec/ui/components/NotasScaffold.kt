package com.bdavidgm.notasrec.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import com.bdavidgm.notasrec.ui.theme.Celeste
import com.bdavidgm.notasrec.ui.theme.CelestePressed
import com.bdavidgm.notasrec.ui.theme.NegroTexto

private val celesteButtonColors: ButtonColors
    @Composable
    get() = ButtonDefaults.elevatedButtonColors(
        containerColor = Celeste,
        contentColor = NegroTexto,
        disabledContainerColor = Celeste.copy(alpha = 0.45f),
        disabledContentColor = NegroTexto.copy(alpha = 0.45f),
    )

@Composable
fun CelesteElevatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    ElevatedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = celesteButtonColors,
        content = content,
    )
}

@Composable
fun CelesteFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = Celeste,
        contentColor = NegroTexto,
        elevation = FloatingActionButtonDefaults.elevation(),
        content = content,
    )
}

/** Botones sobre fondo celeste (top bar): texto negro sin relleno celeste */
@Composable
fun TopBarTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = NegroTexto,
            disabledContentColor = NegroTexto.copy(alpha = 0.38f),
        ),
    ) {
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotasScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = {
            if (snackbarHostState != null) {
                SnackbarHost(snackbarHostState)
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(title, color = NegroTexto) },
                navigationIcon = navigationIcon,
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Celeste,
                    scrolledContainerColor = CelestePressed,
                    navigationIconContentColor = NegroTexto,
                    titleContentColor = NegroTexto,
                    actionIconContentColor = NegroTexto,
                ),
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}
