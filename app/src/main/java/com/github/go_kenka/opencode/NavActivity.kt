/*
 * Copyright 2020 The Android Open Source Project
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

package com.github.go_kenka.opencode

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue.Closed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.github.go_kenka.opencode.components.JetchatDrawer
import com.github.go_kenka.opencode.opencode.OpencodeViewModel
import com.github.go_kenka.opencode.theme.JetchatTheme
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main activity for the app.
 */
class NavActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val opencodeViewModel: OpencodeViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        var keepSplashVisible = true
        installSplashScreen().setKeepOnScreenCondition { keepSplashVisible }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets -> insets }
        opencodeViewModel.startDiscovery()
        lifecycleScope.launch {
            delay(SPLASH_MAX_DURATION_MS)
            keepSplashVisible = false
        }
        lifecycleScope.launch {
            opencodeViewModel.uiState.collect { state ->
                val hasInitializationResult = state.service != null ||
                    state.errorMessage != null ||
                    state.discoveredServices.isNotEmpty() ||
                    !state.isConnecting
                if (hasInitializationResult) {
                    keepSplashVisible = false
                    cancel()
                }
            }
        }

        setContentView(
            ComposeView(this).apply {
                consumeWindowInsets = false
                setContent {
                    JetchatTheme(isDynamicColor = false) {
                        val drawerState = rememberDrawerState(initialValue = Closed)
                        val drawerOpen by viewModel.drawerShouldBeOpened
                            .collectAsStateWithLifecycle()
                        val opencodeState by opencodeViewModel.uiState.collectAsStateWithLifecycle()

                        var selectedMenu by remember { mutableStateOf(opencodeState.selectedProject?.sessionId) }
                        if (drawerOpen) {
                            // Open drawer and reset state in VM.
                            LaunchedEffect(Unit) {
                                // wrap in try-finally to handle interruption whiles opening drawer
                                try {
                                    drawerState.open()
                                } finally {
                                    viewModel.resetOpenDrawerAction()
                                }
                            }
                        }

                        val scope = rememberCoroutineScope()

                        JetchatDrawer(
                            drawerState = drawerState,
                            selectedSessionId = opencodeState.selectedProject?.sessionId ?: selectedMenu,
                            sessions = opencodeState.recentSessions,
                            onSessionClicked = {
                                opencodeViewModel.selectSession(it)
                                findNavController().popBackStack(R.id.nav_home, false)
                                scope.launch {
                                    drawerState.close()
                                }
                                selectedMenu = it
                            },
                            onSettingsClicked = {
                                findNavController().navigate(R.id.nav_settings)
                                scope.launch {
                                    drawerState.close()
                                }
                                selectedMenu = "settings"
                            },
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { context ->
                                        LayoutInflater.from(context).inflate(R.layout.content_main, null, false)
                                    },
                                    update = {
                                        ensureNavHost()
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        return findNavController().navigateUp() || super.onSupportNavigateUp()
    }

    /**
     * See https://issuetracker.google.com/142847973
     */
    private fun findNavController(): NavController {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController
    }

    private fun ensureNavHost() {
        val existing = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        if (existing is NavHostFragment) return
        if (supportFragmentManager.isStateSaved) return
        val navHostFragment = NavHostFragment.create(R.navigation.mobile_navigation)
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, navHostFragment)
            .setPrimaryNavigationFragment(navHostFragment)
            .commitNowAllowingStateLoss()
    }

    private companion object {
        const val SPLASH_MAX_DURATION_MS = 1800L
    }
}
