package com.rts.rys.ryy.drawingtogether.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rts.rys.ryy.drawingtogether.ui.canvas.DrawingScreen
import com.rts.rys.ryy.drawingtogether.ui.home.HomeScreen
import com.rts.rys.ryy.drawingtogether.ui.pairing.PairingScreen
import com.rts.rys.ryy.drawingtogether.ui.preview.PreviewScreen
import com.rts.rys.ryy.drawingtogether.ui.splash.SplashScreen

object Routes {
    const val Splash = "splash"
    const val Home = "home"
    const val Draw = "draw"
    const val Pairing = "pairing"
    const val Preview = "preview"           // path 인자: workId
    const val PreviewArg = "workId"
}

@Composable
fun AppNavGraph(modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = Routes.Splash,
        modifier = modifier,
    ) {
        composable(Routes.Splash) {
            SplashScreen(onFinished = {
                nav.navigate(Routes.Home) {
                    popUpTo(Routes.Splash) { inclusive = true }
                }
            })
        }
        composable(Routes.Home) {
            HomeScreen(
                onSingleMode = { nav.navigate(Routes.Draw) },
                onMultiMode = { nav.navigate(Routes.Pairing) },
                onWorkClick = { workId ->
                    nav.navigate("${Routes.Preview}/$workId")
                },
            )
        }
        composable(Routes.Draw) {
            DrawingScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.Pairing) {
            PairingScreen(
                onBack = { nav.popBackStack() },
                onConnected = {
                    nav.navigate(Routes.Draw) {
                        popUpTo(Routes.Pairing) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = "${Routes.Preview}/{${Routes.PreviewArg}}",
            arguments = listOf(navArgument(Routes.PreviewArg) { type = NavType.StringType }),
        ) { backStackEntry ->
            val workId = backStackEntry.arguments?.getString(Routes.PreviewArg).orEmpty()
            PreviewScreen(workId = workId, onBack = { nav.popBackStack() })
        }
    }
}
