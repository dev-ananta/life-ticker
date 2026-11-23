package com.lifeticker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeticker.ui.HomeScreen
import com.lifeticker.ui.LifeTickerTheme
import com.lifeticker.ui.ListsScreen
import com.lifeticker.ui.StockDetailScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            LifeTickerTheme { App() }
        }
    }
}

private const val HOME = "home"
private const val LISTS = "lists"
private const val STOCK = "stock:"

@Composable
private fun App(vm: TrackerViewModel = viewModel()) {
    val data by vm.data.collectAsState()
    var route by rememberSaveable { mutableStateOf(HOME) }
    // Re-read the clock whenever data changes so "today" is always current.
    val now = remember(data) { System.currentTimeMillis() }

    BackHandler(enabled = route != HOME) { route = HOME }

    when {
        route == LISTS -> ListsScreen(
            data = data,
            onBack = { route = HOME },
            onAddList = { vm.addList(it) },
            onRenameList = vm::renameList,
            onDeleteList = vm::deleteList,
            onSetMembers = vm::setListMembers,
        )

        route.startsWith(STOCK) -> {
            val id = route.removePrefix(STOCK)
            val stock = data.stocks.find { it.id == id }
            if (stock == null) {
                LaunchedEffect(id) { route = HOME }
            } else {
                StockDetailScreen(
                    stock = stock,
                    data = data,
                    now = now,
                    onBack = { route = HOME },
                    onEditStock = { t, n, l -> vm.updateStock(id, t, n, l) },
                    onDeleteStock = { vm.deleteStock(id); route = HOME },
                    onCreateList = vm::addList,
                    onAddEntry = { ts, v, n -> vm.addEntry(id, ts, v, n) },
                    onUpdateEntry = vm::updateEntry,
                    onDeleteEntry = vm::deleteEntry,
                )
            }
        }

        else -> HomeScreen(
            data = data,
            now = now,
            onOpenStock = { route = STOCK + it },
            onOpenLists = { route = LISTS },
            onAddStock = { t, n, l -> vm.addStock(t, n, l) },
            onCreateList = vm::addList,
            onLoadSample = vm::loadSampleData,
        )
    }
}
