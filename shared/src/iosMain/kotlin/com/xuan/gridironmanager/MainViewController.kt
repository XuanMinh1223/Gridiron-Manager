package com.xuan.gridironmanager

import androidx.compose.ui.window.ComposeUIViewController
import com.xuan.gridironmanager.ui.App

@Suppress("ktlint:standard:function-naming") // Factory called by name from Swift
fun MainViewController() = ComposeUIViewController { App() }
