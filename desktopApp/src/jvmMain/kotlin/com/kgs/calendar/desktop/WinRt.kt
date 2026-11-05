package com.kgs.calendar.desktop

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The few calls into the Windows Runtime the app needs: notifications and the Store app's startup
 * task exist nowhere else. Java can't reach it, so these go through the COM tables by hand; each
 * interface's methods start at slot 6, after IUnknown's and IInspectable's, in the order of its
 * metadata, and the identifiers are the ones in Windows' metadata.
 */
internal object WinRt {
    /** Windows Runtime calls run here, on one thread of the multithreaded apartment. */
    private val thread = Executors.newSingleThreadExecutor { task ->
        Thread({
            Combase.INSTANCE.RoInitialize(MULTITHREADED)
            task.run()
        }, "kgs-winrt").apply { isDaemon = true }
    }

    private fun <T> onWinRtThread(work: () -> T): T = thread.submit(Callable(work)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

    /** Shows the notification [toastXml] as the app [appId]. */
    fun showToast(appId: String, toastXml: String) = onWinRtThread {
        val document = hString("Windows.Data.Xml.Dom.XmlDocument") { name ->
            val instance = PointerByReference()
            check(Combase.INSTANCE.RoActivateInstance(name, instance))
            Com(instance.value)
        }
        document.use {
            document.query(IXmlDocumentIO).use { io -> hString(toastXml) { xml -> io.call(FIRST_METHOD, xml) } }
            val toast = factory("Windows.UI.Notifications.ToastNotification", IToastNotificationFactory).use { factory ->
                document.query(IXmlDocument).use { xml -> factory.callForObject(FIRST_METHOD, xml.pointer) }
            }
            toast.use {
                factory("Windows.UI.Notifications.ToastNotificationManager", IToastNotificationManagerStatics).use { manager ->
                    // After CreateToastNotifier without an id.
                    val notifier = hString(appId) { id -> manager.callForObject(FIRST_METHOD + 1, id) }
                    notifier.use { it.call(FIRST_METHOD, toast.pointer) }
                }
            }
        }
    }

    /** The state of the Store app's startup task [taskId]; see [StartupTaskEnabled] and the others. */
    fun startupTaskState(taskId: String): Int = onWinRtThread {
        startupTask(taskId).use { task ->
            val state = IntByReference()
            task.call(FIRST_METHOD + 2, state)
            state.value
        }
    }

    /** Asks Windows to start the app at sign-in; returns the state that leaves the task in. */
    fun enableStartupTask(taskId: String): Int = onWinRtThread {
        startupTask(taskId).use { task ->
            task.callForObject(FIRST_METHOD).use { operation ->
                awaitCompletion(operation)
                val state = IntByReference()
                operation.call(ASYNC_GET_RESULTS, state)
                state.value
            }
        }
    }

    fun disableStartupTask(taskId: String) = onWinRtThread {
        startupTask(taskId).use { task -> task.call(FIRST_METHOD + 1) }
        Unit
    }

    private fun startupTask(taskId: String): Com =
        factory("Windows.ApplicationModel.StartupTask", IStartupTaskStatics).use { statics ->
            // After GetForCurrentPackageAsync.
            hString(taskId) { id -> statics.callForObject(FIRST_METHOD + 1, id) }.use { operation ->
                awaitCompletion(operation)
                operation.callForObject(ASYNC_GET_RESULTS)
            }
        }

    private fun awaitCompletion(operation: Com) {
        operation.query(IAsyncInfo).use { info ->
            val status = IntByReference()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS - 1)
            while (true) {
                info.call(FIRST_METHOD + 1, status)
                when (status.value) {
                    ASYNC_STARTED -> Unit
                    ASYNC_COMPLETED -> return
                    else -> error("Windows Runtime operation ended with status ${status.value}")
                }
                check(System.nanoTime() < deadline) { "Windows Runtime operation timed out" }
                Thread.sleep(POLL_MS)
            }
        }
    }

    private fun factory(className: String, iid: String): Com = hString(className) { name ->
        val factory = PointerByReference()
        check(Combase.INSTANCE.RoGetActivationFactory(name, Guid.GUID(iid), factory))
        Com(factory.value)
    }

    private fun <T> hString(value: String, use: (Pointer) -> T): T {
        val handle = PointerByReference()
        check(Combase.INSTANCE.WindowsCreateString(WString(value), value.length, handle))
        try {
            return use(handle.value)
        } finally {
            Combase.INSTANCE.WindowsDeleteString(handle.value)
        }
    }

    private fun check(result: Int) {
        check(result >= 0) { "Windows Runtime call failed: 0x${Integer.toHexString(result)}" }
    }

    /** A reference to a COM object. */
    private class Com(val pointer: Pointer) : AutoCloseable {
        private fun invoke(slot: Int, arguments: Array<out Any?>): Int {
            val table = pointer.getPointer(0)
            val method = Function.getFunction(table.getPointer(slot.toLong() * Native.POINTER_SIZE))
            return method.invokeInt(arrayOf(pointer, *arguments))
        }

        fun call(slot: Int, vararg arguments: Any?) = check(invoke(slot, arguments))

        fun callForObject(slot: Int, vararg arguments: Any?): Com {
            val result = PointerByReference()
            call(slot, *arguments, result)
            return Com(result.value)
        }

        fun query(iid: String): Com = callForObject(QUERY_INTERFACE, Guid.GUID(iid))

        override fun close() {
            invoke(RELEASE, emptyArray())
        }
    }

    @Suppress("FunctionName")
    private interface Combase : Library {
        fun RoInitialize(type: Int): Int
        fun RoActivateInstance(className: Pointer, instance: PointerByReference): Int
        fun RoGetActivationFactory(className: Pointer, iid: Guid.GUID, factory: PointerByReference): Int
        fun WindowsCreateString(source: WString, length: Int, string: PointerByReference): Int
        fun WindowsDeleteString(string: Pointer?): Int

        companion object {
            val INSTANCE: Combase = Native.load("combase", Combase::class.java)
        }
    }

    const val StartupTaskDisabledByUser = 1
    const val StartupTaskEnabled = 2
    const val StartupTaskEnabledByPolicy = 4

    private const val MULTITHREADED = 1
    private const val TIMEOUT_SECONDS = 10L
    private const val POLL_MS = 10L
    private const val QUERY_INTERFACE = 0
    private const val RELEASE = 2
    private const val FIRST_METHOD = 6
    private const val ASYNC_STARTED = 0
    private const val ASYNC_COMPLETED = 1

    /** In every IAsyncOperation, after Completed's setter and getter. */
    private const val ASYNC_GET_RESULTS = FIRST_METHOD + 2

    private const val IAsyncInfo = "{00000036-0000-0000-C000-000000000046}"
    private const val IXmlDocument = "{f7f3a506-1e87-42d6-bcfb-b8c809fa5494}"
    private const val IXmlDocumentIO = "{6cd0e74e-ee65-4489-9ebf-ca43e87ba637}"
    private const val IToastNotificationFactory = "{04124b20-82c6-4229-b109-fd9ed4662b53}"
    private const val IToastNotificationManagerStatics = "{50ac103f-d235-4598-bbef-98fe4d1a3ad4}"
    private const val IStartupTaskStatics = "{ee5b60bd-a148-41a7-b26e-e8b88a1e62f8}"
}
