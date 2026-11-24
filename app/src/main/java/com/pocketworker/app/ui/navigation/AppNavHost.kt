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
import com.pocketworker.app.ui.projects.ProjectsScreen
import com.pocketworker.app.ui.projects.ProjectsViewModel
import com.pocketworker.app.ui.workspace.WorkspaceScreen
import com.pocketworker.app.ui.workspace.WorkspaceViewModel

private object Routes {
    const val PROJECTS = "projects"
    const val WORKSPACE = "workspace/{projectId}"

    fun workspace(projectId: String): String = "workspace/${Uri.encode(projectId)}"
}

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.PROJECTS
    ) {
        composable(Routes.PROJECTS) {
            val projectsViewModel: ProjectsViewModel = viewModel(
                factory = ProjectsViewModel.factory(
                    gitEngine = container.gitEngine,
                    registry = container.projectRegistry,
                    credentialStore = container.credentialStore
                )
            )
            ProjectsScreen(
                viewModel = projectsViewModel,
                onOpenProject = { projectId ->
                    navController.navigate(Routes.workspace(projectId))
                }
            )
        }

        composable(
            route = Routes.WORKSPACE,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val workspaceViewModel: WorkspaceViewModel = viewModel(
                factory = WorkspaceViewModel.factory(
                    projectId = projectId,
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
