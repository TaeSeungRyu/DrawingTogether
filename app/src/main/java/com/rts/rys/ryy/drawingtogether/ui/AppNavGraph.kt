package com.rts.rys.ryy.drawingtogether.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rts.rys.ryy.drawingtogether.transport.nearby.TransportMode
import com.rts.rys.ryy.drawingtogether.ui.canvas.DrawingScreen
import com.rts.rys.ryy.drawingtogether.ui.home.HomeScreen
import com.rts.rys.ryy.drawingtogether.ui.pairing.PairingScreen
import com.rts.rys.ryy.drawingtogether.ui.pairing.PartyPairingScreen
import com.rts.rys.ryy.drawingtogether.ui.preview.PreviewScreen
import com.rts.rys.ryy.drawingtogether.ui.splash.SplashScreen
import com.rts.rys.ryy.drawingtogether.ui.timelapse.TimelapseGalleryScreen
import com.rts.rys.ryy.drawingtogether.ui.timelapse.TimelapsePlayerScreen

// Draw 화면이 진입하는 모드.
// - Single    : 네트워크 없음, 혼자 그리기
// - Duo       : 1:1 함께 모드 (공유 캔버스)
// - Party     : 1:N 모임 모드 (자기 캔버스 + 모두-미니 뷰, mesh)
// - Classroom : 1:N 교실 모드 (호스트 중심 — 조인자끼리 안 보임). doc/done/classroom-mode.md
enum class DrawMode { Single, Duo, Party, Classroom }

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
    const val ClassroomPairing = "classroom-pairing"  // 교실 모드 (1:N) 페어링
    const val Preview = "preview"             // path 인자: workId
    const val PreviewArg = "workId"
    const val Timelapses = "timelapses"       // 타임랩스 갤러리
    const val Timelapse = "timelapse"         // path 인자: id (재생)
    const val TimelapseArg = "id"
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
                onClassroomMode = { nav.navigate(Routes.ClassroomPairing) },
                onWorkClick = { workId ->
                    nav.navigate("${Routes.Preview}/$workId")
                },
                onTimelapses = { nav.navigate(Routes.Timelapses) },
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
                onExitToHome = {
                    // 모임 호스트 이탈 등 — 홈까지 한 번에 복귀.
                    nav.popBackStack(Routes.Home, inclusive = false)
                },
                onReconnect = { asHost ->
                    // 재연결 흐름(실질적으로 Duo 전용 — 스타 모드는 DrawingScreen 에서 조기 분기).
                    // 원래 역할대로 재진입: 호스트였으면 광고(autoHost=true), 조인자였으면 검색(false).
                    // 둘 다 autoHost=true 로 광고만 하다 교착되던 문제(#7)를 해소.
                    val target = when (mode) {
                        DrawMode.Party -> Routes.PartyPairing
                        DrawMode.Classroom -> Routes.ClassroomPairing
                        else -> pairingRoute(autoHost = asHost)
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
        composable(Routes.ClassroomPairing) {
            // 교실 모드 — 같은 페어링 화면을 Classroom 전송 모드로 재사용(2단계에서 mode 파라미터화).
            PartyPairingScreen(
                mode = TransportMode.Classroom,
                onBack = { nav.popBackStack() },
                onStart = {
                    val target = drawRoute(DrawMode.Classroom)
                    val popped = nav.popBackStack(target, inclusive = false)
                    if (!popped) {
                        nav.navigate(target) {
                            popUpTo(Routes.ClassroomPairing) { inclusive = true }
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
        composable(Routes.Timelapses) {
            TimelapseGalleryScreen(
                onBack = { nav.popBackStack() },
                onPlay = { id -> nav.navigate("${Routes.Timelapse}/$id") },
            )
        }
        composable(
            route = "${Routes.Timelapse}/{${Routes.TimelapseArg}}",
            arguments = listOf(navArgument(Routes.TimelapseArg) { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(Routes.TimelapseArg).orEmpty()
            TimelapsePlayerScreen(id = id, onBack = { nav.popBackStack() })
        }
    }
}
