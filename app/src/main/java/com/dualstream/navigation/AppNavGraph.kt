package com.dualstream.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dualstream.ui.screens.HomeScreen
import com.dualstream.ui.screens.ReceiverScreen
import com.dualstream.ui.screens.SenderScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToSender = { navController.navigate("sender") },
                onNavigateToReceiver = { navController.navigate("receiver") }
            )
        }
        composable("sender") {
            SenderScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("receiver") {
            ReceiverScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
