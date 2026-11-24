package com.pocketworker.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pocketworker.app.AppContainer
import com.pocketworker.app.ui.home.HomeScreen
import com.pocketworker.app.ui.home.HomeViewModel
import com.pocketworker.app.ui.projectdetail.ProjectDetailScreen
import com.pocketworker.app.ui.projectdetail.ProjectDetailViewModel
import com.pocketworker.app.ui.workspace.WorkspaceScreen
import com.pocketworker.app.ui.workspace.WorkspaceViewModel

private object Routes {
    const val HOME = "home"
    const val PROJECT = "project/{projectId}"
    const val REPO_WORKSPACE = "project/{projectId}/repo/{repoId}"

    fun project(projectId: String): String = "project/${Uri.encode(projectId)}"

    fun repoWorkspace(projectId: String, repoId: String): String =
        "project/${Uri.encode(projectId)}/repo/${Uri.encode(repoId)}"
}

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(
                    gitEngine = container.gitEngine,
                    registry = container.projectRegistry,
                    credentialStore = container.credentialStore
                )
            )
            HomeScreen(
                viewModel = homeViewModel,
                onOpenProject = { projectId ->
                    navController.navigate(Routes.project(projectId))
                }
            )
        }

        composable(
            route = Routes.PROJECT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val detailViewModel: ProjectDetailViewModel = viewModel(
                factory = ProjectDetailViewModel.factory(
                    projectId = projectId,
                    gitEngine = container.gitEngine,
                    registry = container.projectRegistry,
                    credentialStore = container.credentialStore
                )
            )
            ProjectDetailScreen(
                viewModel = detailViewModel,
                onBack = { navController.popBackStack() },
                onOpenRepo = { repoId ->
                    navController.navigate(Routes.repoWorkspace(projectId, repoId))
                }
            )
        }

        composable(
            route = Routes.REPO_WORKSPACE,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("repoId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val repoId = backStackEntry.arguments?.getString("repoId") ?: return@composable
            val workspaceViewModel: WorkspaceViewModel = viewModel(
                factory = WorkspaceViewModel.factory(
                    projectId = projectId,
                    repoId = repoId,
                    gitEngine = container.gitEngine,
                    fileManager = container.fileManager,
                    registry = container.projectRegistry,
                    credentialStore = container.credentialStore
                )
            )
            WorkspaceScreen(
                viewModel = workspaceViewModel,
                onBackToProjects = { navController.popBackStack() }
            )
        }
    }
}
