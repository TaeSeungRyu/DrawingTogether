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
    // path: pairing/{autoHost}. autoHost=true 면 진입 직후 자동 startAdvertising —
    // 재연결 시나리오에서 사용자 한 번 더 탭 없이 바로 호스트 광고를 켠다.
    const val Pairing = "pairing"
    const val PairingArg = "autoHost"
    const val PartyPairing = "party-pairing"  // 모임 모드 (1:N) 페어링
    const val Preview = "preview"             // path 인자: workId
    const val PreviewArg = "workId"
}

private fun drawRoute(mode: DrawMode): String = "${Routes.Draw}/${mode.name}"

private fun pairingRoute(autoHost: Boolean): String = "${Routes.Pairing}/$autoHost"

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
                onDuoMode = { nav.navigate(pairingRoute(autoHost = false)) },
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
                    // 재연결 흐름. Duo 면 자동 호스트 광고 시작 (autoHost=true) — 사용자가
                    // 페어링 화면에서 다시 한 번 호스트 버튼을 탭하지 않아도 된다.
                    val target = when (mode) {
                        DrawMode.Party -> Routes.PartyPairing
                        else -> pairingRoute(autoHost = true)
                    }
                    nav.navigate(target)
                },
            )
        }
        composable(
            route = "${Routes.Pairing}/{${Routes.PairingArg}}",
            arguments = listOf(navArgument(Routes.PairingArg) { type = NavType.BoolType }),
        ) { entry ->
            val autoHost = entry.arguments?.getBoolean(Routes.PairingArg) ?: false
            PairingScreen(
                autoStartAsHost = autoHost,
                onBack = { nav.popBackStack() },
                onConnected = {
                    val target = drawRoute(DrawMode.Duo)
                    // 백스택에 이미 Draw(Duo) 가 있으면 (재연결 시나리오) 그쪽으로 pop —
                    // ViewModel + CanvasState 가 살아있어서 끊기기 전 자기 stroke 가 그대로 유지된다.
                    val popped = nav.popBackStack(target, inclusive = false)
                    if (!popped) {
                        nav.navigate(target) {
                            popUpTo("${Routes.Pairing}/{${Routes.PairingArg}}") { inclusive = true }
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
