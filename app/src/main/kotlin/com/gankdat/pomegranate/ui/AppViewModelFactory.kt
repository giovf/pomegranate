package com.gankdat.pomegranate.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gankdat.pomegranate.PomegranateApplication

/**
 * Builds a ViewModel from the application's object graph without a DI
 * framework. The lambda receives the [CreationExtras] so it can call
 * `createSavedStateHandle()` when a destination has arguments.
 */
internal inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: CreationExtras.(PomegranateApplication) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create(checkNotNull(this[APPLICATION_KEY]) as PomegranateApplication) }
}
