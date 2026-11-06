/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedout.app.core.network

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Keyed single-flight execution: concurrent [run] calls for the same key share one execution of
 * the block, so duplicate work is coalesced, including across poll ticks when a slow execution
 * outlives its caller's retry interval. Entries clear themselves on completion, so a later call
 * runs fresh.
 *
 * Failures resolve to null for every joined caller (logged once), retry policy is left to
 * callers.
 *
 * Concurrency-safe: the in-flight map is a [ConcurrentHashMap], whose atomic `computeIfAbsent`
 * coalesces racing callers, so [run] may be invoked concurrently from any context. The coalesced
 * block always runs on [scope]'s dispatcher, not the caller's. A caller that leaves while waiting
 * does not cancel the block: it lives in [scope] until done.
 *
 * MTGA's copy, taken as it is (the same reader base):
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/network/SingleFlight.kt#SingleFlight
 * Ported from OneBusAway's SingleFlight:
 * https://github.com/OneBusAway/onebusaway-android/blob/main/onebusaway-android/src/main/java/org/onebusaway/android/util/SingleFlight.kt#SingleFlight
 * (Apache-2.0). One change, from Go's singleflight (BSD-3-Clause):
 * https://github.com/golang/sync/blob/master/singleflight/singleflight.go#doCall
 * (`if g.m[key] == c { delete(g.m, key) }`): the entry is removed only if it still
 * holds this very call, never a newer one for the same key.
 */
class SingleFlight<K : Any, V : Any>(private val scope: CoroutineScope) {

    private val inFlight = ConcurrentHashMap<K, Deferred<V?>>()

    /** Runs [block] for [key] in [scope], or joins the in-flight execution for the same key. */
    suspend fun run(key: K, block: suspend () -> V?): V? = inFlight
        // computeIfAbsent is atomic, so concurrent callers for one key share a Deferred.
        // The coroutine is LAZY: await() (below) starts it only after computeIfAbsent has
        // returned and stored the Deferred, so the finally/remove can never run
        // re-entrantly, even for an inline/undispatched dispatcher and a
        // non-suspending block. For the same reason [call] is assigned before the block runs.
        .computeIfAbsent(key) {
            lateinit var call: Deferred<V?>
            call = scope.async(start = CoroutineStart.LAZY) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e // scope cancelled, propagate, don't mask as a null result
                } catch (e: Exception) {
                    Log.e(TAG, "Single-flight block failed for $key", e)
                    null
                } finally {
                    inFlight.remove(key, call)
                }
            }
            call
        }
        .await()

    companion object {
        private const val TAG = "SingleFlight"
    }
}
