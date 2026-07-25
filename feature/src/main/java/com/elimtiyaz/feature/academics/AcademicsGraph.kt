package com.elimtiyaz.feature.academics

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.elimtiyaz.app.navigation.Route

/**
 * Academics feature navigation graph.
 *
 * Wired from the root [com.elimtiyaz.app.navigation.ElImtiyazNavHost]. The
 * Academics hub (`Route.Academics`) is a bottom-nav destination; the four
 * nested routes are full-screen with their own TopAppBar + back button.
 *
 * Routes used (all already defined in `Routes.kt`):
 * - [Route.Academics]   — hub tab (Classes / Matières / Devoirs).
 * - [Route.ClassDetail] — single class with roster + subjects + attendance + grades.
 * - [Route.RollCall]    — 30-second roll call per §09.01.
 * - [Route.GradeEntry]  — table of D1/D2/Examen for one class+subject+term.
 * - [Route.HomeworkPush] — push a new homework assignment with attachments.
 */
fun NavGraphBuilder.academicsGraph(nav: NavController) {
    composable(Route.Academics.route) {
        AcademicsHubScreen(nav)
    }
    composable(
        route = Route.ClassDetail.route,
        arguments = listOf(navArgument("classId") { type = NavType.StringType }),
    ) { backStack ->
        val classId = backStack.arguments?.getString("classId").orEmpty()
        ClassDetailScreen(classId, nav)
    }
    composable(
        route = Route.RollCall.route,
        arguments = listOf(navArgument("classId") { type = NavType.StringType }),
    ) { backStack ->
        val classId = backStack.arguments?.getString("classId").orEmpty()
        RollCallScreen(classId, nav)
    }
    composable(
        route = Route.GradeEntry.route,
        arguments = listOf(
            navArgument("classId") { type = NavType.StringType },
            navArgument("subjectId") { type = NavType.StringType },
        ),
    ) { backStack ->
        val classId = backStack.arguments?.getString("classId").orEmpty()
        val subjectId = backStack.arguments?.getString("subjectId").orEmpty()
        GradeEntryScreen(classId, subjectId, nav)
    }
    composable(
        route = Route.HomeworkPush.route,
        arguments = listOf(navArgument("classId") { type = NavType.StringType }),
    ) { backStack ->
        val classId = backStack.arguments?.getString("classId").orEmpty()
        HomeworkPushScreen(classId, nav)
    }
}
