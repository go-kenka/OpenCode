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

package com.github.go_kenka.opencode.conversation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.go_kenka.opencode.MainViewModel
import com.github.go_kenka.opencode.opencode.OpencodeViewModel
import com.github.go_kenka.opencode.opencode.model.OpenCodePermissionResponse
import com.github.go_kenka.opencode.theme.JetchatTheme

class ConversationFragment : Fragment() {
    private val activityViewModel: MainViewModel by activityViewModels()
    private val viewModel: OpencodeViewModel by activityViewModels()
    private val nearbyWifiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startDiscovery()
        } else {
            viewModel.markDiscoveryPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureDiscoveryPermission()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)

            setContent {
                JetchatTheme(isDynamicColor = false) {
                    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
                    OpenCodeConversationScreen(
                        uiState = uiState,
                        onProjectSelected = viewModel::selectProject,
                        onProjectDeleteConfirmed = viewModel::deleteProject,
                        onAddProjectClick = viewModel::showAddProjectPicker,
                        onAddProjectDismiss = viewModel::dismissAddProjectPicker,
                        onAddProjectDirectoryOpen = viewModel::openProjectDirectory,
                        onAddProjectDirectoryUp = viewModel::openProjectDirectoryParent,
                        onAddProjectSearchQueryChange = viewModel::updateProjectPickerQuery,
                        onAddProjectConfirm = viewModel::addProjectFromCurrentDirectory,
                        onModeSelected = viewModel::selectMode,
                        onModelSelected = viewModel::selectModel,
                        onThinkingSelected = viewModel::selectThinking,
                        onMessageSent = viewModel::sendMessage,
                        onAbortSending = viewModel::abortSending,
                        onPermissionAllowOnce = { viewModel.respondPermission(OpenCodePermissionResponse.ONCE) },
                        onPermissionAllowAlways = { viewModel.respondPermission(OpenCodePermissionResponse.ALWAYS) },
                        onPermissionDeny = { viewModel.respondPermission(OpenCodePermissionResponse.DENY) },
                        serverOptions = uiState.discoveredServices,
                        onServerSelected = viewModel::selectService,
                        showServicePicker = uiState.pendingServiceSelection,
                        discoveredServices = uiState.discoveredServices,
                        onDiscoveredServiceSelected = viewModel::selectService,
                        onDiscoveredServicePickerDismiss = viewModel::dismissDiscoveredServicePicker,
                        onMenuClick = { activityViewModel.openDrawer() },
                        onServerConfigClick = ::ensureDiscoveryPermission,
                        onCreateSessionClick = viewModel::createNewSession,
                        onFileUploadClick = {
                            // TODO: Wire file picker and upload flow.
                        },
                        onRetryDiscovery = ::ensureDiscoveryPermission,
                    )
                }
            }
        }

    private fun ensureDiscoveryPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            viewModel.startDiscovery()
            return
        }

        val permission = Manifest.permission.NEARBY_WIFI_DEVICES
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startDiscovery()
        } else {
            nearbyWifiPermissionLauncher.launch(permission)
        }
    }
}
