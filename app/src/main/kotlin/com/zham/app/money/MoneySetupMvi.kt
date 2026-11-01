package com.zham.app.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewModelScope
import com.zham.app.ui.ComposeViewModel
import com.zham.domain.model.Account
import com.zham.domain.model.AccountRepository
import com.zham.domain.model.MoneyCategory
import com.zham.domain.model.MoneyCategoryRepository
import com.zham.domain.model.TransactionRepository
import com.zham.domain.usecase.accountBalance
import com.zham.domain.usecase.formatMinor
import com.zham.domain.usecase.parseAmount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class AccountItem(
    val id: Long,
    val name: String,
    val currency: String,
    val balance: String,
    val initial: String,
    val color: Color,
    val icon: String,
    val archived: Boolean,
)

@Immutable
data class AccountsState(val accounts: ImmutableList<AccountItem>, val error: String?)

sealed interface AccountsEvent {
    data class Save(
        val id: Long,
        val name: String,
        val currency: String,
        val initial: String,
        val color: Color,
        val icon: String,
        val archived: Boolean,
    ) : AccountsEvent

    data class Delete(val id: Long) : AccountsEvent
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) : ComposeViewModel<AccountsState, AccountsEvent>() {

    private val error = mutableStateOf<String?>(null)

    @Composable
    override fun uiState(): AccountsState {
        val accounts by remember { accountRepository.observeAccounts() }.collectAsState(emptyList())
        val transactions by remember { transactionRepository.observeTransactions() }.collectAsState(emptyList())
        return AccountsState(
            accounts = accounts.map {
                AccountItem(
                    it.id, it.name, it.currency, formatMinor(accountBalance(it, transactions), it.currency),
                    formatMinor(it.initialMinor).replace(" ", ""), Color(it.color), it.icon, it.archived,
                )
            }.toImmutableList(),
            error = error.value,
        )
    }

    override fun onEvent(event: AccountsEvent) {
        error.value = null
        when (event) {
            is AccountsEvent.Save -> viewModelScope.launch {
                val code = event.currency.trim().uppercase()
                val negative = event.initial.trim().startsWith("-")
                val initial = if (event.initial.isBlank()) 0L else parseAmount(event.initial.trim().removePrefix("-"))
                when {
                    event.name.isBlank() -> error.value = "Enter a name."
                    code.length != 3 || !code.all(Char::isLetter) -> error.value = "The currency is a three-letter code, like AMD or USD."
                    initial == null -> error.value = "The starting balance is not a number."
                    else -> {
                        val existing = accountRepository.observeAccounts().first()
                        val order = existing.firstOrNull { it.id == event.id }?.orderNum
                            ?: ((existing.maxOfOrNull { it.orderNum } ?: -1.0) + 1.0)
                        accountRepository.save(
                            Account(
                                event.id, event.name.trim(), code, event.color.toArgb(), event.icon,
                                if (negative) -initial else initial, order, event.archived,
                            )
                        )
                    }
                }
            }
            is AccountsEvent.Delete -> viewModelScope.launch { accountRepository.delete(event.id) }
        }
    }
}

@Immutable
data class MoneyCategoryItem(val id: Long, val name: String, val color: Color, val icon: String)

@Immutable
data class MoneyCategoriesState(val categories: ImmutableList<MoneyCategoryItem>, val error: String?)

sealed interface MoneyCategoriesEvent {
    data class Save(val id: Long, val name: String, val color: Color, val icon: String) : MoneyCategoriesEvent
    data class Delete(val id: Long) : MoneyCategoriesEvent
}

@HiltViewModel
class MoneyCategoriesViewModel @Inject constructor(
    private val repository: MoneyCategoryRepository,
) : ComposeViewModel<MoneyCategoriesState, MoneyCategoriesEvent>() {

    private val error = mutableStateOf<String?>(null)

    @Composable
    override fun uiState(): MoneyCategoriesState {
        val list by remember { repository.observeCategories() }.collectAsState(emptyList())
        return MoneyCategoriesState(
            list.map { MoneyCategoryItem(it.id, it.name, Color(it.color), it.icon) }.toImmutableList(),
            error.value,
        )
    }

    override fun onEvent(event: MoneyCategoriesEvent) {
        error.value = null
        when (event) {
            is MoneyCategoriesEvent.Save -> viewModelScope.launch {
                if (event.name.isBlank()) {
                    error.value = "Enter a name."
                    return@launch
                }
                val existing = repository.observeCategories().first()
                val order = existing.firstOrNull { it.id == event.id }?.orderNum
                    ?: ((existing.maxOfOrNull { it.orderNum } ?: -1.0) + 1.0)
                repository.save(MoneyCategory(event.id, event.name.trim(), event.color.toArgb(), event.icon, order))
            }
            is MoneyCategoriesEvent.Delete -> viewModelScope.launch { repository.delete(event.id) }
        }
    }
}
