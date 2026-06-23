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
import com.rts.rys.ryy.drawingtogether.ui.pairing.PartyPairingScreen
import com.rts.rys.ryy.drawingtogether.ui.preview.PreviewScreen
import com.rts.rys.ryy.drawingtogether.ui.splash.SplashScreen

// Phase 4-D: Draw 화면이 셋 중 하나의 모드로 진입한다.
// - Single  : 네트워크 없음, 혼자 그리기
// - Duo     : 1:1 함께 모드 (공유 캔버스)
// - Party   : 1:N 모임 모드 (자기 캔버스 + 미니 뷰)
enum class DrawMode { Single, Duo, Party }

object Routes {
    const val Splash = "splash"
    const val Home = "home"
    // path: draw/{mode}
    const val Draw = "draw"
    const val DrawArg = "mode"
    const val Pairing = "pairing"             // 함께 모드 (1:1) 페어링
    const val PartyPairing = "party-pairing"  // 모임 모드 (1:N) 페어링
    const val Preview = "preview"             // path 인자: workId
    const val PreviewArg = "workId"
}

private fun drawRoute(mode: DrawMode): String = "${Routes.Draw}/${mode.name}"

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
                onSingleMode = { nav.navigate(drawRoute(DrawMode.Single)) },
                onDuoMode = { nav.navigate(Routes.Pairing) },
                onPartyMode = { nav.navigate(Routes.PartyPairing) },
                onWorkClick = { workId ->
                    nav.navigate("${Routes.Preview}/$workId")
                },
            )
        }
        composable(
            route = "${Routes.Draw}/{${Routes.DrawArg}}",
            arguments = listOf(navArgument(Routes.DrawArg) { type = NavType.StringType }),
        ) { entry ->
            val modeName = entry.arguments?.getString(Routes.DrawArg) ?: DrawMode.Single.name
            val mode = runCatching { DrawMode.valueOf(modeName) }.getOrDefault(DrawMode.Single)
            DrawingScreen(
                mode = mode,
                onBack = { nav.popBackStack() },
                onReconnect = {
                    val target = when (mode) {
                        DrawMode.Party -> Routes.PartyPairing
                        else -> Routes.Pairing
                    }
                    nav.navigate(target)
                },
            )
        }
        composable(Routes.Pairing) {
            PairingScreen(
                onBack = { nav.popBackStack() },
                onConnected = {
                    val target = drawRoute(DrawMode.Duo)
                    // 백스택에 이미 Draw(Duo) 가 있으면 (재연결 시나리오) 그쪽으로 pop —
                    // ViewModel + CanvasState 가 살아있어서 끊기기 전 자기 stroke 가 그대로 유지된다.
                    val popped = nav.popBackStack(target, inclusive = false)
                    if (!popped) {
                        nav.navigate(target) {
                            popUpTo(Routes.Pairing) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(Routes.PartyPairing) {
            PartyPairingScreen(
                onBack = { nav.popBackStack() },
                onStart = {
                    val target = drawRoute(DrawMode.Party)
                    val popped = nav.popBackStack(target, inclusive = false)
                    if (!popped) {
                        nav.navigate(target) {
                            popUpTo(Routes.PartyPairing) { inclusive = true }
                        }
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
