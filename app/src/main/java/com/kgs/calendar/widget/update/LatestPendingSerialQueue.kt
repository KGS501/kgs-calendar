package com.kgs.calendar.widget.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class LatestPendingSerialQueue<K : Any, V : Any>(
    private val scope: CoroutineScope,
    private val onSuperseded: (V) -> Unit = {},
    /** Told about a failed [worker]; the queue itself carries on with what is pending. */
    private val onFailure: (Throwable) -> Unit = {},
    private val worker: suspend (V) -> Unit,
) {
    private data class State<V : Any>(
        var running: Job? = null,
        var pending: V? = null,
    )

    private val lock = Any()
    private val states = mutableMapOf<K, State<V>>()

    fun submit(key: K, value: V) {
        var superseded: V? = null
        synchronized(lock) {
            val state = states.getOrPut(key) { State() }
            if (state.running?.isActive == true) {
                superseded = state.pending
                state.pending = value
            } else {
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    drain(key, value)
                }
                state.running = job
                job.start()
            }
        }
        superseded?.let(onSuperseded)
    }

    fun cancel(key: K) {
        val removed = synchronized(lock) {
            states.remove(key)
        }
        removed?.pending?.let(onSuperseded)
        removed?.running?.cancel()
    }

    private suspend fun drain(key: K, initial: V) {
        val currentJob = currentCoroutineContext()[Job]
        var value = initial
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    worker(value)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // One failed update must not take the newer one waiting behind it down too.
                    onFailure(error)
                }
                val next = synchronized(lock) {
                    val state = states[key] ?: return
                    state.pending.also { pending ->
                        if (pending == null) {
                            states.remove(key)
                        } else {
                            state.pending = null
                        }
                    }
                } ?: return
                value = next
            }
        } finally {
            // Whoever waits on what was pending gets an answer, however this drain ended.
            val abandoned = synchronized(lock) {
                val state = states[key]
                if (state?.running === currentJob) {
                    states.remove(key)
                    state?.pending
                } else {
                    null
                }
            }
            abandoned?.let(onSuperseded)
        }
    }
}
