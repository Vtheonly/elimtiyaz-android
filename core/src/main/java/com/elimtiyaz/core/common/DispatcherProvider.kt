package com.elimtiyaz.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Indirection over [Dispatchers] so we can swap dispatchers in tests.
 * Provided via Hilt as a singleton in `:data`.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val main get() = Dispatchers.Main
    override val io get() = Dispatchers.IO
    override val default get() = Dispatchers.Default
    override val unconfined get() = Dispatchers.Unconfined
}
