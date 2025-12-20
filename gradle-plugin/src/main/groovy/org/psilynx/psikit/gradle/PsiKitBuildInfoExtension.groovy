package org.psilynx.psikit.gradle

import org.gradle.api.Project

class PsiKitBuildInfoExtension {
    /** Override the package name for the generated class. */
    String packageName = 'org.psilynx.psikit.buildinfo'

    /** Override the generated class name. */
    String className = 'PsiKitBuildInfo'

    /** Override the git repo directory. Defaults to rootProject.projectDir. */
    File repoDir

    PsiKitBuildInfoExtension(Project project) {
        this.repoDir = project.rootProject.projectDir
    }
}
