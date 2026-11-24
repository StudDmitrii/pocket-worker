package com.pocketworker.app

import android.app.Application
import com.pocketworker.app.core.git.AndroidSystemReader
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.JGitEngine
import com.pocketworker.app.core.git.ProjectFileManager
import com.pocketworker.app.core.storage.CredentialStore
import com.pocketworker.app.core.storage.KeystoreCredentialStore
import com.pocketworker.app.core.storage.ProjectRegistry
import java.io.File

class AppContainer(application: Application) {
    val gitEngine: GitEngine = JGitEngine()
    val fileManager: ProjectFileManager = ProjectFileManager()
    val credentialStore: CredentialStore = KeystoreCredentialStore(application)
    val projectRegistry: ProjectRegistry = ProjectRegistry(
        metadataFile = File(application.filesDir, "projects_registry.json"),
        projectsRootDir = File(application.filesDir, "projects")
    )
}

class PocketWorkerApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AndroidSystemReader.install(File(filesDir, "git_config"))
        container = AppContainer(this)
    }
}
