package com.bdavidgm.notasrec.ui.tagcloud

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdavidgm.notasrec.R
import com.bdavidgm.notasrec.ui.components.CelesteElevatedButton
import com.bdavidgm.notasrec.ui.components.NotasScaffold
import com.bdavidgm.notasrec.ui.theme.CelesteClaro
import com.bdavidgm.notasrec.ui.theme.CelesteOscuro
import com.bdavidgm.notasrec.ui.theme.NegroTexto
import kotlin.math.max

@Composable
fun TagCloudScreen(
    viewModel: TagCloudViewModel,
    navigationIcon: @Composable () -> Unit,
    onGoToFilteredNotes: (selectedTagIds: Set<Long>) -> Unit,
) {
    NotasScaffold(
        title = stringResource(R.string.tag_cloud_title),
        navigationIcon = navigationIcon,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TagCloudChipsPane(
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            TagCloudFooter(
                viewModel = viewModel,
                onGoToFilteredNotes = onGoToFilteredNotes,
            )
        }
    }
}

@Composable
private fun TagCloudChipsPane(
    viewModel: TagCloudViewModel,
    modifier: Modifier = Modifier,
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val onToggle = remember(viewModel) { viewModel::toggleTag }

    Box(modifier = modifier) {
        if (tags.isEmpty()) {
            Text(
                text = stringResource(R.string.tag_cloud_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
        } else {
            SimpleFlowRow(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalGap = 10.dp,
                verticalGap = 10.dp,
            ) {
                tags.forEach { tag ->
                    TagCloudChip(
                        tag = tag,
                        onToggle = onToggle,
                    )
                }
            }
        }
    }
}

@Composable
private fun TagCloudChip(
    tag: TagCloudViewModel.TagCloudItem,
    onToggle: (Long) -> Unit,
) {
    ElevatedButton(
        onClick = { onToggle(tag.tagId) },
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = 8.dp,
        ),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = if (tag.selected) CelesteOscuro else CelesteClaro,
            contentColor = NegroTexto,
        ),
    ) {
        Box(
            modifier = Modifier
                .weight(weight = 1f, fill = false)
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = tag.name,
                fontSize = 15.sp,
                fontWeight = if (tag.selected) FontWeight.Bold else FontWeight.Medium,
                color = NegroTexto,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.width(8.dp))
        TagCountBadge(count = tag.noteCount)
    }
}

/** Círculo con el número de notas que usan la etiqueta. */
@Composable
private fun TagCountBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(
                color = Color.White,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = NegroTexto,
            maxLines = 1,
        )
    }
}

@Composable
private fun TagCloudFooter(
    viewModel: TagCloudViewModel,
    onGoToFilteredNotes: (selectedTagIds: Set<Long>) -> Unit,
) {
    val selectedIds by viewModel.selectedTagIds.collectAsStateWithLifecycle()
    val matchingCount by viewModel.matchingNotesCount.collectAsStateWithLifecycle()

    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectedIds.isEmpty()) {
                    stringResource(R.string.tag_cloud_status_none)
                } else {
                    stringResource(R.string.tag_cloud_status_count, matchingCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            )
            CelesteElevatedButton(
                onClick = { onGoToFilteredNotes(selectedIds) },
                enabled = selectedIds.isNotEmpty(),
            ) {
                Text(stringResource(R.string.tag_cloud_go))
            }
        }
    }
}

/** Flujo de chips sin FlowRow (evita NoSuchMethodError con BOM Compose actual). */
@Composable
private fun SimpleFlowRow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 8.dp,
    verticalGap: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        val hGapPx = horizontalGap.roundToPx()
        val vGapPx = verticalGap.roundToPx()
        val maxWidth = constraints.maxWidth

        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }

        var x = 0
        var y = 0
        var rowHeight = 0
        var totalHeight = 0
        val positions = ArrayList<Pair<Int, Int>>(placeables.size)

        placeables.forEach { placeable ->
            if (x > 0 && x + placeable.width > maxWidth) {
                x = 0
                y += rowHeight + vGapPx
                rowHeight = 0
            }
            positions.add(x to y)
            rowHeight = max(rowHeight, placeable.height)
            x += placeable.width + hGapPx
            totalHeight = max(totalHeight, y + rowHeight)
        }

        val width = maxWidth.coerceAtLeast(constraints.minWidth)
        val height = totalHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val (px, py) = positions[index]
                placeable.placeRelative(px, py)
            }
        }
    }
}