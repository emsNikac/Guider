package com.example.guider.ui.util

import androidx.compose.runtime.Immutable

/**
 * Stable UI boundary wrappers for repository collections that are replaced, never mutated.
 * Their data-class equality is structural, so unchanged content can be skipped by Compose.
 */
@Immutable
data class ImmutableListSnapshot<T>(
    private val backing: List<T>,
) : List<T> by backing

@Immutable
data class ImmutableMapSnapshot<K, V>(
    private val backing: Map<K, V>,
) : Map<K, V> by backing

fun <T> List<T>.toImmutableSnapshot(): ImmutableListSnapshot<T> =
    ImmutableListSnapshot(toList())

fun <K, V> Map<K, V>.toImmutableSnapshot(): ImmutableMapSnapshot<K, V> =
    ImmutableMapSnapshot(toMap())
