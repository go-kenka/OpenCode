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

package com.github.go_kenka.opencode.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue.Closed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import com.github.go_kenka.opencode.opencode.model.OpenCodeSession
import com.github.go_kenka.opencode.theme.OpenCodeTheme
import com.github.go_kenka.opencode.theme.opencodeTokens

@Composable
fun OpenCodeDrawer(
    drawerState: DrawerState = rememberDrawerState(initialValue = Closed),
    selectedSessionId: String?,
    sessions: List<OpenCodeSession>,
    onSessionClicked: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    OpenCodeTheme {
        val tokens = opencodeTokens()
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerState = drawerState,
                    drawerContainerColor = MaterialTheme.colorScheme.background,
                    drawerContentColor = MaterialTheme.colorScheme.onBackground,
                    drawerShape = RoundedCornerShape(
                        topEnd = tokens.shapes.large,
                        bottomEnd = tokens.shapes.large,
                    ),
                ) {
                    OpenCodeDrawerContent(
                        sessions = sessions,
                        selectedSessionId = selectedSessionId,
                        onSessionClicked = onSessionClicked,
                    )
                }
            },
            content = content,
        )
    }
}
