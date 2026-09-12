package com.linkedout.app.core.common

/**
 * Result type used across every layer boundary. Nothing throws across a
 * boundary in LinkedOut, failures travel as values so they can be diagnosed.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Success)?.value

fun <T> Outcome<T>.errorOrNull(): AppError? = (this as? Outcome.Failure)?.error
