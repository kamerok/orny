package com.kamer.orny.data.google

import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import com.kamer.orny.data.android.ReactiveActivities
import com.kamer.orny.data.mapping.toExpense
import com.kamer.orny.data.mapping.toList
import com.kamer.orny.data.model.Expense
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import timber.log.Timber


class GoogleRepoImpl(val googleAuthHolder: GoogleAuthHolder, val reactiveActivities: ReactiveActivities)
    : GoogleRepo {

    companion object {
        private const val SPREADSHEET_ID = "1YsFrfpNzs_gjdtnqVNuAPPYl3NRjeo8GgEWAOD7BdOg"
        private const val SHEET_NAME = "Тест"
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
                val expense = value.toExpense()
                Timber.d(expense.toString())
                list.add(expense)
            }
        }

        return list
    }

    private fun addExpenseToApi(service: Sheets, expense: Expense) {
        val writeData: MutableList<MutableList<Any>> = ArrayList()
        writeData.add(expense.toList())
        val valueRange = ValueRange()
        valueRange.majorDimension = "ROWS"
        valueRange.setValues(writeData)
        val request = service.spreadsheets().values().append(SPREADSHEET_ID, SHEET_NAME, valueRange).setValueInputOption("USER_ENTERED")
        Timber.d(request.toString())
        val response = request.execute()
        Timber.d(response.toString())
    }

}