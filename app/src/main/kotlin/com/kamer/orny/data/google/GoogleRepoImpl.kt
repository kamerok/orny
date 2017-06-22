package com.kamer.orny.data.google

import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import com.kamer.orny.data.android.ReactiveActivities
import com.kamer.orny.data.model.Author
import com.kamer.orny.data.model.Expense
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import timber.log.Timber
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class GoogleRepoImpl(val googleAuthHolder: GoogleAuthHolder, val reactiveActivities: ReactiveActivities)
    : GoogleRepo {

    companion object {
        private const val SPREADSHEET_ID = "1YsFrfpNzs_gjdtnqVNuAPPYl3NRjeo8GgEWAOD7BdOg"
        private const val SHEET_NAME = "Тест"

        private val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    }

    override fun getAllExpenses(): Single<List<Expense>> = getSheetsService()
            .flatMap { service ->
                Single
                        .fromCallable { getAllExpensesFromApi(service) }
                        .retryWhen(this::recoverFromGoogleError)
            }

    override fun addExpense(expense: Expense): Completable = getSheetsService()
            .flatMapCompletable { service ->
                Completable
                        .fromAction { addExpenseToApi(service, expense) }
                        .retryWhen(this::recoverFromGoogleError)
            }

    private fun recoverFromGoogleError(attempts: Flowable<Throwable>) = attempts
            .flatMap { error ->
                if (error is UserRecoverableAuthIOException) {
                    reactiveActivities.recoverGoogleAuthException(error)
                            //emit item to resubscribe
                            .toSingle { "" }
                            .toFlowable()
                } else {
                    Flowable.error<Any>(error)
                }
            }

    private fun getSheetsService(): Single<Sheets> = googleAuthHolder.getActiveCredentials()
            .map(this::createSheetsService)

    private fun createSheetsService(credential: GoogleAccountCredential): Sheets {
        val transport = AndroidHttp.newCompatibleTransport()
        val jsonFactory = JacksonFactory.getDefaultInstance()
        return Sheets.Builder(transport, jsonFactory, credential).build()
    }

    private fun getAllExpensesFromApi(service: Sheets): MutableList<Expense> {
        val response = service.spreadsheets().values()
                .get(SPREADSHEET_ID, SHEET_NAME + "!A11:E")
                .execute()
        val list = mutableListOf<Expense>()
        for (value in response.getValues()) {
            Timber.d(value.toString())
            if (value.isNotEmpty()) {
                val expense = expenseFromList(value)
                Timber.d(expense.toString())
                list.add(expense)
            }
        }

        return list
    }

    private fun addExpenseToApi(service: Sheets, expense: Expense) {
        val writeData: MutableList<MutableList<Any>> = ArrayList()
        writeData.add(expenseToList(expense))
        val valueRange = ValueRange()
        valueRange.majorDimension = "ROWS"
        valueRange.setValues(writeData)
        val request = service.spreadsheets().values().append(SPREADSHEET_ID, SHEET_NAME, valueRange).setValueInputOption("USER_ENTERED")
        Timber.d(request.toString())
        val response = request.execute()
        Timber.d(response.toString())
    }

    private fun expenseToList(expense: Expense): MutableList<Any> {
        val dataRow: MutableList<Any> = ArrayList()
        dataRow.add(expense.comment)
        dataRow.add(DATE_FORMAT.format(expense.date))
        dataRow.add(if (expense.isOffBudget) "1" else "0")
        if (expense.author?.id != "0") {
            dataRow.add("")
        }
        dataRow.add(expense.amount.toString())
        return dataRow
    }

    private fun expenseFromList(list: MutableList<Any>): Expense {
        val comment = list[0].toString()
        val date = try {
            DATE_FORMAT.parse(list[1].toString())
        } catch (e: ParseException) {
            Date()
        }
        val isOffBudget = list[2].toString() == "1"
        val authorId = if (list[3].toString().isNullOrEmpty()) "1" else "0"
        val amount = if (list[3].toString().isNullOrEmpty()) list[4].toString().toDouble() else list[3].toString().toDouble()
        return Expense(
                comment = comment,
                date = date,
                isOffBudget = isOffBudget,
                author = Author(id = authorId, name = if (authorId == "0") "Лена" else "Макс", color = ""),
                amount = amount
        )
    }

}