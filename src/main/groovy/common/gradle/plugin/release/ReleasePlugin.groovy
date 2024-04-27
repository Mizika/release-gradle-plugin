package common.gradle.plugin.release

import nebula.plugin.release.ReleaseExtension
import nebula.plugin.release.git.base.ReleasePluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class ReleasePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        println("Apply release plugin")
        def rootProject = project.rootProject

        if (!applyPlugin(rootProject, nebula.plugin.release.ReleasePlugin)) return

        def releaseExtension = rootProject.extensions.findByType(ReleasePluginExtension)
        if (releaseExtension == null) {
            return
        }
        releaseExtension.versionStrategy GitlabCIStrategies.SNAPSHOT(rootProject)
        releaseExtension.versionStrategy GitlabCIStrategies.IMMUTABLE_SNAPSHOT(rootProject)
        releaseExtension.versionStrategy GitlabCIStrategies.DEVELOPMENT(rootProject)
        releaseExtension.versionStrategy GitlabCIStrategies.PRE_RELEASE(rootProject)
        releaseExtension.versionStrategy GitlabCIStrategies.FINAL(rootProject)

        def nebulaReleaseExtension = rootProject.extensions.findByType(ReleaseExtension)
        if (nebulaReleaseExtension == null) {
            nebulaReleaseExtension = rootProject.extensions.create(nebula.plugin.release.ReleasePlugin.NEBULA_RELEASE_EXTENSION_NAME, ReleaseExtension)
        }

        nebulaReleaseExtension.addReleaseBranchPattern(/(candidate(-|\/))?\d+(\.\d+)?\.x/)
        GitlabCIStrategies.BuildMetadata.nebulaReleaseExtension = nebulaReleaseExtension
        rootProject.allprojects {
            it.version = rootProject.version
        }
    }


    private static boolean applyPlugin(Project project, Class<? extends Plugin> pluginType) {
        if (project == null) {
            return false
        }
        if (project.plugins.findPlugin(pluginType) == null) {
            project.logger.quiet("Apply plugin '${pluginType.simpleName}' to '${project.path}'")
            project.plugins.apply(pluginType)
            return true
        } else {
            return false
        }
    }
}
