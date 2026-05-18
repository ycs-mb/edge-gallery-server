package com.smartscreenshot.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartscreenshot.ui.detail.DetailScreen
import com.smartscreenshot.ui.home.HomeScreen
import com.smartscreenshot.ui.settings.SettingsScreen

object Routes {
  const val HOME = "home"
  const val SETTINGS = "settings"
  const val DETAIL = "detail/{screenshotId}"

  fun detail(screenshotId: Long) = "detail/$screenshotId"
}

@Composable
fun AppNavHost() {
  val navController = rememberNavController()
  NavHost(navController = navController, startDestination = Routes.HOME) {
    composable(Routes.HOME) {
      HomeScreen(
        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
        onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
      )
    }
    composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
    composable(
      route = Routes.DETAIL,
      arguments = listOf(navArgument("screenshotId") { type = NavType.LongType }),
    ) { backStackEntry ->
      val id = backStackEntry.arguments?.getLong("screenshotId") ?: -1L
      DetailScreen(screenshotId = id, onBack = { navController.popBackStack() })
    }
  }
}
