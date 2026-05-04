/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.go_kenka.opencode.opencode.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.github.go_kenka.opencode.opencode.model.OpenCodeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

interface OpenCodeDiscovery {
    val state: StateFlow<OpenCodeDiscoveryState>

    fun start()

    fun stop()
}

sealed interface OpenCodeDiscoveryState {
    data object Idle : OpenCodeDiscoveryState
    data object Searching : OpenCodeDiscoveryState
    data class Found(val service: OpenCodeService) : OpenCodeDiscoveryState
    data class Error(val message: String) : OpenCodeDiscoveryState
}

class NsdOpenCodeDiscovery(
    context: Context,
) : OpenCodeDiscovery {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val pendingResolve = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val resolveInProgress = AtomicBoolean(false)
    private val activeServiceTypes = mutableSetOf<String>()
    private val queuedServices = mutableSetOf<String>()
    private val _state = MutableStateFlow<OpenCodeDiscoveryState>(OpenCodeDiscoveryState.Idle)
    private var multicastLock: WifiManager.MulticastLock? = null

    override val state: StateFlow<OpenCodeDiscoveryState> = _state.asStateFlow()

    override fun start() {
        if (discoveryListeners.isNotEmpty()) return

        acquireMulticastLock()
        _state.value = OpenCodeDiscoveryState.Searching
        discoverServiceTypes()
        DEFAULT_SERVICE_TYPES.forEach(::discoverServiceType)
    }

    override fun stop() {
        val listeners = discoveryListeners.toList()
        discoveryListeners.clear()
        activeServiceTypes.clear()
        pendingResolve.clear()
        queuedServices.clear()
        resolveInProgress.set(false)
        listeners.forEach { listener -> runCatching { nsdManager.stopServiceDiscovery(listener) } }
        releaseMulticastLock()
        _state.value = OpenCodeDiscoveryState.Idle
    }

    private fun discoverServiceTypes() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val discoveredType = serviceInfo.serviceName.trimServiceType()
                if (discoveredType.contains("opencode", ignoreCase = true)) {
                    discoverServiceType(discoveredType)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _state.value = OpenCodeDiscoveryState.Error("mDNS 服务类型枚举失败：$errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        discoveryListeners += listener
        runCatching {
            nsdManager.discoverServices(
                SERVICE_ENUMERATION_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                listener,
            )
        }.onFailure { throwable ->
            _state.value = OpenCodeDiscoveryState.Error("mDNS 服务类型枚举异常：${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun discoverServiceType(serviceType: String) {
        val normalizedType = serviceType.trimServiceType()
        if (!activeServiceTypes.add(normalizedType)) return

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _state.value = OpenCodeDiscoveryState.Searching
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.looksLikeOpenCode()) {
                    enqueueResolve(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                queuedServices.remove(serviceInfo.queueKey())
                if (_state.value is OpenCodeDiscoveryState.Found) {
                    _state.value = OpenCodeDiscoveryState.Searching
                }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                activeServiceTypes.remove(normalizedType)
                if (normalizedType in DEFAULT_SERVICE_TYPES.map { it.trimServiceType() }) {
                    _state.value = OpenCodeDiscoveryState.Error("mDNS 启动失败：$errorCode")
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        discoveryListeners += listener
        runCatching {
            nsdManager.discoverServices(
                normalizedType,
                NsdManager.PROTOCOL_DNS_SD,
                listener,
            )
        }.onFailure { throwable ->
            activeServiceTypes.remove(normalizedType)
            _state.value = OpenCodeDiscoveryState.Error("mDNS 启动异常：${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun enqueueResolve(serviceInfo: NsdServiceInfo) {
        if (!queuedServices.add(serviceInfo.queueKey())) return
        pendingResolve.offer(serviceInfo)
        resolveNext()
    }

    private fun resolveNext() {
        if (!resolveInProgress.compareAndSet(false, true)) return
        val serviceInfo = pendingResolve.poll()
        if (serviceInfo == null) {
            resolveInProgress.set(false)
            return
        }

        runCatching {
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        queuedServices.remove(serviceInfo.queueKey())
                        resolveInProgress.set(false)
                        resolveNext()
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        resolveInProgress.set(false)
                        val host = serviceInfo.host?.hostAddress
                        if (host.isNullOrBlank() || serviceInfo.port <= 0) {
                            queuedServices.remove(serviceInfo.queueKey())
                            resolveNext()
                            return
                        }
                        _state.value = OpenCodeDiscoveryState.Found(
                            OpenCodeService(
                                serviceName = serviceInfo.serviceName,
                                host = host,
                                port = serviceInfo.port,
                            ),
                        )
                        resolveNext()
                    }
                },
            )
        }.onFailure {
            queuedServices.remove(serviceInfo.queueKey())
            resolveInProgress.set(false)
            resolveNext()
        }
    }

    private fun acquireMulticastLock() {
        val lock = wifiManager?.createMulticastLock("opencode-mdns") ?: return
        lock.setReferenceCounted(false)
        runCatching { lock.acquire() }
        multicastLock = lock
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        multicastLock = null
    }

    private fun NsdServiceInfo.looksLikeOpenCode(): Boolean {
        return serviceName.contains("opencode", ignoreCase = true) ||
            serviceType.contains("opencode", ignoreCase = true)
    }

    private fun NsdServiceInfo.queueKey(): String = "${serviceType.trimServiceType()}::$serviceName"

    private fun String.trimServiceType(): String {
        val normalized = lowercase(Locale.US).trim().trimEnd('.')
        return if (normalized.endsWith(".local")) normalized.removeSuffix(".local") else normalized
    }

    private companion object {
        val DEFAULT_SERVICE_TYPES = listOf("_opencode._tcp.", "_http._tcp.")
        const val SERVICE_ENUMERATION_TYPE = "_services._dns-sd._udp."
    }
}
