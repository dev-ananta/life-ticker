@file:OptIn(ExperimentalMaterial3Api::class)

package com.lifeticker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifeticker.data.AppData
import com.lifeticker.data.StockList

@Composable
fun ListsScreen(
    data: AppData,
    onBack: () -> Unit,
    onAddList: (String) -> Unit,
    onRenameList: (String, String) -> Unit,
    onDeleteList: (String) -> Unit,
    onSetMembers: (String, Set<String>) -> Unit,
) {
    var showNew by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<StockList?>(null) }
    var deleting by remember { mutableStateOf<StockList?>(null) }
    var members by remember { mutableStateOf<StockList?>(null) }
    val byId = data.stocks.associateBy { it.id }

    Scaffold(
        containerColor = Palette.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Lists", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Bg),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNew = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New list") },
                containerColor = Palette.Up, contentColor = Color.Black,
            )
        },
    ) { pad ->
        if (data.lists.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No lists yet", fontWeight = FontWeight.Bold)
                Text("Group your stocks — Romance, Career, Health, Friends…", color = Palette.TextDim)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 96.dp),
            ) {
                items(data.lists, key = { it.id }) { l ->
                    val tickers = l.stockIds.mapNotNull { byId[it]?.ticker }
                    Column(Modifier.clickable { members = l }) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(l.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (tickers.isEmpty()) "Empty — tap to add stocks" else tickers.joinToString(" · "),
                                    color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { renaming = l }) { Icon(Icons.Filled.Edit, "Rename") }
                            IconButton(onClick = { deleting = l }) { Icon(Icons.Filled.Delete, "Delete") }
                        }
                        HorizontalDivider(color = Palette.Divider, modifier = Modifier.padding(start = 20.dp))
                    }
                }
            }
        }
    }

    if (showNew) {
        NameDialog("New list", "List name (e.g. Romance)", "", "Create",
            onDismiss = { showNew = false }, onSave = { onAddList(it); showNew = false })
    }
    renaming?.let { l ->
        NameDialog("Rename list", "List name", l.name, "Save",
            onDismiss = { renaming = null }, onSave = { onRenameList(l.id, it); renaming = null })
    }
    deleting?.let { l ->
        ConfirmDialog(
            title = "Delete ${l.name}?",
            message = "The stocks in it are kept — only the list is removed.",
            confirmText = "Delete",
            onDismiss = { deleting = null },
            onConfirm = { onDeleteList(l.id); deleting = null },
        )
    }
    members?.let { l ->
        ListMembersDialog(
            list = l, stocks = data.stocks,
            onDismiss = { members = null },
            onSave = { onSetMembers(l.id, it); members = null },
        )
    }
}
