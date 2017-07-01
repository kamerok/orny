package com.kamer.orny.presentation.editexpense

import android.arch.lifecycle.LiveData
import com.kamer.orny.data.domain.model.Author
import com.kamer.orny.presentation.core.SingleLiveEvent
import java.util.*


interface EditExpenseViewModel {

    fun bindAuthors(): LiveData<List<Author>>
    fun bindDate(): LiveData<Date>
    fun bindSavingProgress(): LiveData<Boolean>
    fun bindShowDatePicker(): SingleLiveEvent<Date>
    fun bindShowExitDialog(): SingleLiveEvent<Nothing>
    fun bindShowAmountError(): SingleLiveEvent<String>
    fun bindShowError(): SingleLiveEvent<String>

    fun amountChanged(amountRaw: String)
    fun commentChanged(comment: String)
    fun authorSelected(author: Author)
    fun dateChanged(date: Date)
    fun offBudgetChanged(isOffBudget: Boolean)
    fun selectDate()
    fun exitScreen()
    fun confirmExit()
    fun saveExpense()

}