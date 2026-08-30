package com.example.guider.domain.collections

import androidx.compose.runtime.Immutable

/** Stable collection boundaries for immutable snapshots that are replaced, never mutated. */
@Immutable
data class ImmutableListSnapshot<T>(
    private val backing: List<T>,
) : List<T> by backing

@Immutable
data class ImmutableMapSnapshot<K, V>(
    private val backing: Map<K, V>,
) : Map<K, V> by backing

@Immutable
data class ImmutableSetSnapshot<T>(
    private val backing: Set<T>,
) : Set<T> by backing

fun <T> List<T>.toImmutableSnapshot(): ImmutableListSnapshot<T> =
    ImmutableListSnapshot(toList())

fun <K, V> Map<K, V>.toImmutableSnapshot(): ImmutableMapSnapshot<K, V> =
    ImmutableMapSnapshot(toMap())

fun <T> Set<T>.toImmutableSnapshot(): ImmutableSetSnapshot<T> =
    ImmutableSetSnapshot(toSet())
