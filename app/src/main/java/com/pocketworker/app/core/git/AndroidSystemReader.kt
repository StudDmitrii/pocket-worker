package com.pocketworker.app.core.git

import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader
import java.io.File

/**
 * Custom JGit SystemReader for Android so user/system gitconfigs reside inside
 * app-private storage instead of unreadable/non-writable OS paths like /etc/gitconfig.
 */
class AndroidSystemReader(
    private val delegate: SystemReader,
    private val configDirectory: File
) : SystemReader() {

    private val userConfigFile = File(configDirectory, "gitconfig")
    private val systemConfigFile = File(configDirectory, "system_gitconfig")
    private val jgitConfigFile = File(configDirectory, "jgit_config")

    init {
        if (!configDirectory.exists()) {
            configDirectory.mkdirs()
        }
    }

    override fun getHostname(): String = delegate.hostname ?: "android-device"

    override fun getenv(variable: String): String? {
        if (variable == "HOME") {
            return configDirectory.absolutePath
        }
        return delegate.getenv(variable)
    }

    override fun getProperty(key: String): String? {
        if (key == "user.home") {
            return configDirectory.absolutePath
        }
        return delegate.getProperty(key)
    }

    override fun openUserConfig(parent: Config?, fs: FS?): FileBasedConfig {
        return FileBasedConfig(parent, userConfigFile, fs ?: FS.DETECTED)
    }

    override fun openSystemConfig(parent: Config?, fs: FS?): FileBasedConfig {
        return FileBasedConfig(parent, systemConfigFile, fs ?: FS.DETECTED)
    }

    override fun openJGitConfig(parent: Config?, fs: FS?): FileBasedConfig {
        return FileBasedConfig(parent, jgitConfigFile, fs ?: FS.DETECTED)
    }

    override fun getCurrentTime(): Long = delegate.currentTime

    override fun getTimezone(`when`: Long): Int = delegate.getTimezone(`when`)

    companion object {
        @Volatile
        private var installed = false

        @Synchronized
        fun install(configDirectory: File) {
            if (installed) return
            val current = getInstance()
            if (current !is AndroidSystemReader) {
                setInstance(AndroidSystemReader(current, configDirectory))
            }
            installed = true
        }
    }
}
