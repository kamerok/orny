package com.kamer.orny.presentation.core

import android.support.v7.app.AppCompatActivity
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable

abstract class BaseActivity : AppCompatActivity() {

    private val compositeDisposable = CompositeDisposable()

    protected fun <T> Single<T>.disposeOnDestroy(): Single<T> = doOnSubscribe { compositeDisposable.add(it) }
    protected fun <T> Observable<T>.disposeOnDestroy(): Observable<T> = doOnSubscribe { compositeDisposable.add(it) }
    protected fun Completable.disposeOnDestroy(): Completable = doOnSubscribe { compositeDisposable.add(it) }

    override fun onDestroy() {
        compositeDisposable.dispose()
        super.onDestroy()
    }

}