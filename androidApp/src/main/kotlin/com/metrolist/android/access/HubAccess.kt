package com.metrolist.android.access

import android.content.Context
import android.os.SystemClock
import com.metrolist.android.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class HubAccessState(
    val allowed: Boolean = false,
    val checking: Boolean = true,
    val bound: Boolean = false,
    val message: Int = R.string.xmusic_access_intro,
)

internal class HubAccess(
    context: Context,
) {
    private val vault = HubCredentialVault(context)
    private val client = HubAccessClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(HubAccessState())
    val state = mutableState.asStateFlow()
    private var credentials: HubCredentials? = null

    @Volatile private var validUntil = 0L

    @Volatile private var signingOut = false
    private var started = false

    fun isAllowed(): Boolean = !signingOut && SystemClock.elapsedRealtime() < validUntil

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            mutex.withLock {
                credentials = vault.load()
                verifyLocked()
            }
            while (isActive) {
                delay(60_000)
                mutex.withLock { verifyLocked() }
            }
        }
        scope.launch {
            while (isActive) {
                delay(1000)
                // Expiry closes the player even while the next HTTP check is waiting.
                val current = mutableState.value
                if (current.allowed && !isAllowed()) {
                    mutableState.compareAndSet(current, current.copy(allowed = false, message = R.string.xmusic_access_unavailable))
                }
            }
        }
    }

    fun refresh() {
        if (mutableState.value.checking) return
        mutableState.value = mutableState.value.copy(allowed = isAllowed(), checking = true)
        scope.launch { mutex.withLock { verifyLocked() } }
    }

    fun activate(code: String) {
        if (!code.matches(Regex("[0-9]{6}")) || mutableState.value.checking) return
        mutableState.value = mutableState.value.copy(checking = true)
        scope.launch {
            mutex.withLock {
                try {
                    val candidate = client.activate(code)
                    if (candidate == null) {
                        publish(R.string.xmusic_access_invalid)
                        return@withLock
                    }
                    when (val decision = client.verify(candidate)) {
                        is HubDecision.Active -> {
                            vault.save(candidate)
                            credentials = candidate
                            validUntil = decision.until
                            publish()
                        }

                        HubDecision.Inactive -> {
                            publish(R.string.xmusic_access_invalid)
                        }

                        HubDecision.Unavailable -> {
                            publish(R.string.xmusic_access_unavailable)
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    publish(R.string.xmusic_access_unavailable)
                }
            }
        }
    }

    fun signOut() {
        signingOut = true
        mutableState.value = HubAccessState(checking = true)
        scope.launch {
            mutex.withLock {
                validUntil = 0
                credentials = null
                vault.clear()
                signingOut = false
                publish()
            }
        }
    }

    private suspend fun verifyLocked() {
        val current = credentials
        if (current == null) {
            publish()
            return
        }
        when (val decision = client.verify(current)) {
            is HubDecision.Active -> {
                validUntil = decision.until
                publish()
            }

            HubDecision.Inactive -> {
                validUntil = 0
                publish(R.string.xmusic_access_invalid)
            }

            HubDecision.Unavailable -> {
                publish(R.string.xmusic_access_unavailable)
            }
        }
    }

    private fun publish(message: Int = R.string.xmusic_access_intro) {
        mutableState.value = HubAccessState(isAllowed(), checking = signingOut, bound = credentials != null, message = message)
    }
}
