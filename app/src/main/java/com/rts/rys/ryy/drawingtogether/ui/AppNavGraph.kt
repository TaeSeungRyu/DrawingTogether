package com.rts.rys.ryy.drawingtogether.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rts.rys.ryy.drawingtogether.ui.canvas.DrawingScreen
import com.rts.rys.ryy.drawingtogether.ui.home.HomeScreen
import com.rts.rys.ryy.drawingtogether.ui.pairing.PairingScreen
import com.rts.rys.ryy.drawingtogether.ui.splash.SplashScreen

object Routes {
    const val Splash = "splash"
    const val Home = "home"
    const val Draw = "draw"
    const val Pairing = "pairing"
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
            )
        }
        composable(Routes.Draw) {
            DrawingScreen()
        }
        composable(Routes.Pairing) {
            PairingScreen(onBack = { nav.popBackStack() })
        }
    }
}
