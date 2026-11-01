package com.kgs.calendar.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/** Converts one editor field to the plain values an [EditorDraftStore] keeps, and back. */
internal class DraftCodec<T>(val save: (T) -> Any?, val restore: (Any?) -> T) {
    companion object {
        val Text = DraftCodec<String>({ it }, { it as String })
        val OptionalText = DraftCodec<String?>({ it }, { it as String? })
        val Number = DraftCodec<Int>({ it }, { (it as kotlin.Number).toInt() })
        val OptionalNumber = DraftCodec<Int?>({ it }, { (it as kotlin.Number?)?.toInt() })
        val Flag = DraftCodec<Boolean>({ it }, { it as Boolean })
        val OptionalFlag = DraftCodec<Boolean?>({ it }, { it as Boolean? })

        /** Reminder offsets keep their order. */
        val MinuteSet = DraftCodec<Set<Int>>(
            { ArrayList(it) },
            { saved -> (saved as List<*>).mapTo(LinkedHashSet()) { (it as kotlin.Number).toInt() } },
        )
    }
}

/**
 * The fields of one editor session, stored under [draftId] in [store]. Each field behaves like
 * `rememberSaveable(inputs)`: the first time it is composed it takes the draft's value if there
 * is one (after a rotation or process death), and it is reset to `init()` whenever its inputs
 * change later on. Without a draft ID the fields only live in the composition.
 */
@Stable
internal class EditorDraftFields(private val store: EditorDraftStore, private val draftId: String?) {
    private val composedFields = HashSet<String>()

    @Composable
    fun <T> field(name: String, codec: DraftCodec<T>, vararg inputs: Any?, init: () -> T): MutableState<T> =
        remember(this, *inputs) { newState(name, codec, init) }

    /** Called when [name] is first composed and whenever its inputs change. */
    internal fun <T> newState(name: String, codec: DraftCodec<T>, init: () -> T): MutableState<T> {
        val restored = if (composedFields.add(name)) restore(name, codec) else null
        val initial = if (restored != null) restored.getOrThrow() else init()
        put(name, codec.save(initial))
        return DraftFieldState(initial) { put(name, codec.save(it)) }
    }

    private fun <T> restore(name: String, codec: DraftCodec<T>): Result<T>? {
        val values = draftId?.let(store::values) ?: return null
        if (!values.containsKey(name)) return null
        return runCatching { codec.restore(values[name]) }.takeIf { it.isSuccess }
    }

    private fun put(name: String, value: Any?) {
        draftId?.let { store.put(it, name, value) }
    }
}

@Composable
internal fun rememberEditorDraftFields(store: EditorDraftStore, draftId: String?): EditorDraftFields =
    remember(store, draftId) { EditorDraftFields(store, draftId) }

private class DraftFieldState<T>(initial: T, private val onChange: (T) -> Unit) : MutableState<T> {
    private val state = mutableStateOf(initial)

    override var value: T
        get() = state.value
        set(value) {
            state.value = value
            onChange(value)
        }

    override fun component1(): T = value

    override fun component2(): (T) -> Unit = { value = it }
}
