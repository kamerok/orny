package com.kamer.orny.data.google

import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.AppendCellsRequest
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.RowData
import com.kamer.orny.data.android.ReactiveActivities
import com.kamer.orny.data.google.exceptions.NotSupportedSheetException
import com.kamer.orny.data.google.model.GoogleExpense
import com.kamer.orny.data.google.model.GooglePage
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import timber.log.Timber
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.*


class GoogleRepoImpl(val googleAuthHolder: GoogleAuthHolder, val reactiveActivities: ReactiveActivities)
    : GoogleRepo {

    companion object {
        private const val APP_PAGE_ID = "AppSupportedSheet"

        private const val SPREADSHEET_ID = "1YsFrfpNzs_gjdtnqVNuAPPYl3NRjeo8GgEWAOD7BdOg"
        private const val SHEET_NAME = "Июнь"
        private const val SHEET_ID = 1549946213

        val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    }

    override fun getAllExpenses(): Single<List<GoogleExpense>> = getSheetsService()
            .flatMap { service ->
                Single
                        .fromCallable { getAllExpensesFromApi(service) }
                        .retryWhen(this::recoverFromGoogleError)
            }

    override fun getPage(): Single<GooglePage> = getSheetsService()
            .flatMap { service ->
                Single
                        .fromCallable { getPageFromApi(service) }
                        .retryWhen(this::recoverFromGoogleError)
            }

    override fun addExpense(googleExpense: GoogleExpense): Completable = getSheetsService()
            .flatMapCompletable { service ->
                Completable
                        .fromAction { addExpenseToApi(service, googleExpense) }
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

    private fun getAllExpensesFromApi(service: Sheets): List<GoogleExpense> {
        val response = service.spreadsheets().values()
                .get(SPREADSHEET_ID, SHEET_NAME + "!A11:E")
                .execute()
        val list = mutableListOf<GoogleExpense>()
        for (value in response.getValues()) {
            Timber.d(value.toString())
            if (value.isNotEmpty()) {
                val expense = GoogleExpense.fromList(value)
                Timber.d(expense.toString())
                list.add(expense)
            }
        }

        return list
    }

    private fun getPageFromApi(service: Sheets): GooglePage {
        val response = service.spreadsheets().values()
                .batchGet(SPREADSHEET_ID)
                .setRanges(listOf(
                        SHEET_NAME + "!A1",
                        SHEET_NAME + "!A2:C2",
                        SHEET_NAME + "!D10:H10",
                        SHEET_NAME + "!A11:H"
                ))
                .execute()
        Timber.d(response.toString())

        val firstCell = response.valueRanges.firstOrNull()?.getValues()?.firstOrNull()?.firstOrNull()
        val isSupportedPage = firstCell != null && firstCell.toString() == APP_PAGE_ID
        if (!isSupportedPage) throw NotSupportedSheetException()

        val setting = response.valueRanges[1].getValues().first()
        val budget = setting[0].toString().toDouble()
        val days = setting[1].toString().toInt()
        val date = try {
            GoogleRepoImpl.DATE_FORMAT.parse(setting[2].toString())
        } catch (e: IllegalArgumentException) {
            Date()
        }

        val authors = response.valueRanges[2].getValues().first().map { it.toString() }

        val expenses = response.valueRanges[3].getValues().map { GoogleExpense.fromList(it) }

        return GooglePage(budget, days, date, authors, expenses)
    }

    private fun addExpenseToApi(service: Sheets, expense: GoogleExpense) {
        val rowData = listOf<RowData>(RowData().setValues(expense.toCells()))

        val appendCellRequest = AppendCellsRequest().apply {
            sheetId = SHEET_ID
            rows = rowData
            fields = "userEnteredValue,userEnteredFormat.numberFormat"
        }

        val batchRequests = BatchUpdateSpreadsheetRequest().apply {
            requests = listOf(Request().setAppendCells(appendCellRequest))
        }

        val request = service.spreadsheets().batchUpdate(SPREADSHEET_ID, batchRequests)
        Timber.d("$request")

        val response = request.execute()
        Timber.d("$response")
    }

}