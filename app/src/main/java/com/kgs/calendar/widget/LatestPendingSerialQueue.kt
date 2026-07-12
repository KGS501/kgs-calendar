package com.kgs.calendar.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class LatestPendingSerialQueue<K : Any, V : Any>(
    private val scope: CoroutineScope,
    private val onSuperseded: (V) -> Unit = {},
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
                worker(value)
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
            synchronized(lock) {
                if (states[key]?.running === currentJob) {
                    states.remove(key)
                }
            }
        }
    }
}
