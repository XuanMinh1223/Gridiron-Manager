package com.xuan.gridironmanager

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.xuan.gridironmanager.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Gridiron Manager",
    ) {
        App()
    }
}