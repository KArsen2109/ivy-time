package com.zham.app.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.app.ui.ColorPicker
import com.zham.app.ui.GlyphPicker
import com.zham.app.ui.rememberDeleteConfirm
import com.zham.design.system.BrightPalette
import com.zham.design.system.ChoiceChip
import com.zham.design.system.EmptyNote
import com.zham.design.system.GlyphIcon
import com.zham.design.system.ScreenHeading
import com.zham.design.system.Spacing
import com.zham.design.system.ZhamCard
import com.zham.design.system.ZhamEmojis
import com.zham.design.system.ZhamTheme

@Composable
fun AccountsScreen(modifier: Modifier = Modifier) {
    val viewModel: AccountsViewModel = hiltViewModel()
    val state = viewModel.uiState()
    var edited by remember { mutableStateOf<AccountItem?>(null) }
    var dialog by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            ScreenHeading("Accounts") {
                IconButton(onClick = { edited = null; dialog = true }) { Icon(Icons.Rounded.Add, contentDescription = "New account") }
            }
            if (state.accounts.isEmpty()) EmptyNote("Cash, a card, savings: each is an account with its own balance.")
        }
        items(state.accounts, key = { it.id }) { account ->
            ZhamCard(
                Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp),
                onClick = { edited = account; dialog = true },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(account.icon, tint = account.color, size = 24.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(account.name + if (account.archived) " (hidden)" else "", style = MaterialTheme.typography.titleMedium)
                        Text(account.balance, style = MaterialTheme.typography.bodySmall, color = ZhamTheme.extra.slate)
                    }
                    val askDelete = rememberDeleteConfirm(
                        "Delete this account?", "Every entry on it, and every transfer to or from it, is deleted too.",
                    ) { viewModel.onEvent(AccountsEvent.Delete(account.id)) }
                    IconButton(onClick = askDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete account") }
                }
            }
        }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.Gutter)) } }
    }

    if (dialog) {
        val initial = edited
        var name by remember { mutableStateOf(initial?.name.orEmpty()) }
        var currency by remember { mutableStateOf(initial?.currency ?: defaultCurrency()) }
        var start by remember { mutableStateOf(initial?.initial ?: "0") }
        var color by remember { mutableStateOf(initial?.color ?: BrightPalette[22]) }
        var icon by remember { mutableStateOf(initial?.icon ?: ZhamEmojis.key("💵")) }
        var archived by remember { mutableStateOf(initial?.archived ?: false) }
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text(if (initial == null) "New account" else "Edit account") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                    OutlinedTextField(
                        currency, { currency = it.take(3).uppercase() }, label = { Text("Currency code") },
                        singleLine = true, shape = MaterialTheme.shapes.medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("AMD", "USD", "EUR", "RUB").forEach { code ->
                            ChoiceChip(code, currency == code, MaterialTheme.colorScheme.primary, onClick = { currency = code })
                        }
                    }
                    OutlinedTextField(
                        start, { start = it }, label = { Text("Balance now") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.medium,
                    )
                    ColorPicker(color, onSelect = { color = it })
                    GlyphPicker(icon, color, onSelect = { icon = it })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hide from the balance", Modifier.weight(1f))
                        com.zham.design.system.ZhamSwitch(archived, { archived = it })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    viewModel.onEvent(AccountsEvent.Save(initial?.id ?: 0L, name, currency, start, color, icon, archived))
                    dialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } },
        )
    }
}

private fun defaultCurrency(): String =
    runCatching { java.util.Currency.getInstance(java.util.Locale.getDefault()).currencyCode }.getOrDefault("USD")

@Composable
fun MoneyCategoriesScreen(modifier: Modifier = Modifier) {
    val viewModel: MoneyCategoriesViewModel = hiltViewModel()
    val state = viewModel.uiState()
    var edited by remember { mutableStateOf<MoneyCategoryItem?>(null) }
    var dialog by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            ScreenHeading("Money categories") {
                IconButton(onClick = { edited = null; dialog = true }) { Icon(Icons.Rounded.Add, contentDescription = "New category") }
            }
            if (state.categories.isEmpty()) EmptyNote("What money goes on and comes from: food, transport, salary.")
        }
        items(state.categories, key = { it.id }) { category ->
            ZhamCard(Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp), onClick = { edited = category; dialog = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(category.icon, tint = category.color, size = 24.dp)
                    Text(category.name, Modifier.weight(1f).padding(horizontal = 14.dp), style = MaterialTheme.typography.titleMedium)
                    val askDelete = rememberDeleteConfirm(
                        "Delete this category?", "Entries keep their amounts but lose the category.",
                    ) { viewModel.onEvent(MoneyCategoriesEvent.Delete(category.id)) }
                    IconButton(onClick = askDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete category") }
                }
            }
        }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.Gutter)) } }
    }

    if (dialog) {
        val initial = edited
        var name by remember { mutableStateOf(initial?.name.orEmpty()) }
        var color by remember { mutableStateOf(initial?.color ?: BrightPalette[3]) }
        var icon by remember { mutableStateOf(initial?.icon ?: ZhamEmojis.key("⭐")) }
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text(if (initial == null) "New category" else "Edit category") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                    ColorPicker(color, onSelect = { color = it })
                    GlyphPicker(icon, color, onSelect = { icon = it })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    viewModel.onEvent(MoneyCategoriesEvent.Save(initial?.id ?: 0L, name, color, icon))
                    dialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } },
        )
    }
}
