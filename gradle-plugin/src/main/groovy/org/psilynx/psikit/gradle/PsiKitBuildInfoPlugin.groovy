package org.psilynx.psikit.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class PsiKitBuildInfoPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        def ext = project.extensions.create('psiKitBuildInfo', PsiKitBuildInfoExtension, project)

        def outDirProvider = project.layout.buildDirectory.dir('generated/source/psikitBuildInfo')

        def taskProvider = project.tasks.register('generatePsiKitBuildInfo', GeneratePsiKitBuildInfoTask) { t ->
            t.outputDir.set(outDirProvider)
            t.repoDir.set(project.rootProject.layout.projectDirectory)
            t.className.convention('PsiKitBuildInfo')
            t.packageName.convention('')

            // Capture Maven-ish identifiers at configuration time (config-cache safe).
            t.mavenGroup.convention(project.rootProject.name)
            t.mavenName.convention(project.name)
            t.versionString.convention(project.version == null ? 'unspecified' : project.version.toString())
        }

        def configureForAndroid = {
            project.afterEvaluate {
                def android = project.extensions.findByName('android')
                if (android == null) {
                    project.logger.warn('PsiKitBuildInfoPlugin: android extension not found; skipping wiring.')
                    return
                }

                def pkg = ext.packageName

                taskProvider.configure { t ->
                    t.packageName.set(pkg)
                    t.className.set(ext.className ?: 'PsiKitBuildInfo')
                    t.repoDir.set(project.layout.dir(project.provider { ext.repoDir ?: project.rootProject.projectDir }))

                    // Track git state without scanning the whole repo.
                    def gitDir = new File((ext.repoDir ?: project.rootProject.projectDir), '.git')
                    t.inputs.file(new File(gitDir, 'HEAD')).optional()
                    t.inputs.file(new File(gitDir, 'index')).optional()
                    t.inputs.file(new File(gitDir, 'packed-refs')).optional()
                    t.inputs.dir(new File(gitDir, 'refs')).optional()
                    t.inputs.dir(new File(gitDir, 'logs')).optional()
                }

                // Add generated sources.
                def outDir = outDirProvider.get().asFile
                android.sourceSets.main.java.srcDir(outDir)

                // Ensure build info exists before compilation.
                project.tasks.matching { it.name == 'preBuild' }.configureEach {
                    dependsOn(taskProvider)
                }
            }
        }

        project.pluginManager.withPlugin('com.android.application') { configureForAndroid() }
        project.pluginManager.withPlugin('com.android.library') { configureForAndroid() }
    }
}
