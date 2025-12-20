package org.psilynx.psikit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GeneratePsiKitBuildInfoTask extends DefaultTask {
    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    @Internal
    abstract DirectoryProperty getRepoDir()

    @Input
    abstract Property<String> getPackageName()

    @Input
    abstract Property<String> getClassName()

    @Input
    abstract Property<String> getMavenGroup()

    @Input
    abstract Property<String> getMavenName()

    @Input
    abstract Property<String> getVersionString()

    private static String escapeJava(String s) {
        if (s == null) return ''
        return s
            .replace('\\\\', '\\\\\\\\')
            .replace('"', '\\"')
            .replace('\r', '')
            .replace('\n', ' ')
    }

    private static String runGit(File repoDir, List<String> gitArgs, boolean allowEmpty) {
        try {
            def pb = new ProcessBuilder(['git'] + gitArgs)
            pb.directory(repoDir)
            pb.redirectErrorStream(true)
            def p = pb.start()
            def out = p.inputStream.getText('UTF-8')
            p.waitFor()
            if (p.exitValue() != 0) return null
            def text = (out == null ? '' : out).trim()
            if (allowEmpty) return text
            return text.isEmpty() ? null : text
        } catch (ignored) {
            return null
        }
    }

    @TaskAction
    void generate() {
        def repo = repoDir.get().asFile

        // Git metadata (matches the fields AdvantageKit expects from gversion)
        def gitSha = runGit(repo, ['rev-parse', 'HEAD'], false) ?: 'unknown'
        def gitBranch = runGit(repo, ['rev-parse', '--abbrev-ref', 'HEAD'], false) ?: 'unknown'
        def gitDate = runGit(repo, ['log', '-1', '--format=%cI'], false) ?: 'unknown'

        def revisionText = runGit(repo, ['rev-list', '--count', 'HEAD'], false)
        def gitRevision = -1
        try {
            if (revisionText != null) gitRevision = Integer.parseInt(revisionText.trim())
        } catch (ignored) {
            gitRevision = -1
        }

        def status = runGit(repo, ['status', '--porcelain'], true)
        def dirty = (status == null) ? -1 : (status.isEmpty() ? 0 : 1)

        def nowMillis = System.currentTimeMillis()
        def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        sdf.setTimeZone(java.util.TimeZone.getTimeZone('UTC'))
        def buildDate = sdf.format(new Date(nowMillis))

        def pkg = packageName.get()
        def cls = className.get()

        def mavenGroup = mavenGroup.get()
        def mavenName = mavenName.get()
        def versionString = versionString.get()

        def outBase = outputDir.get().asFile
        // Clear stale outputs (e.g., if package/class name changes between builds).
        if (outBase.exists()) {
            outBase.deleteDir()
        }
        outBase.mkdirs()
        def pkgDir = new File(outBase, pkg.replace('.', '/'))
        pkgDir.mkdirs()

        def outFile = new File(pkgDir, "${cls}.java")
        outFile.text = (
            "package ${pkg};\n\n"
            + "/**\n"
            + " * Automatically generated file containing build version information.\n"
            + " */\n"
            + "public final class ${cls} {\n"
            + "\tpublic static final String MAVEN_GROUP = \"${escapeJava(mavenGroup)}\";\n"
            + "\tpublic static final String MAVEN_NAME = \"${escapeJava(mavenName)}\";\n"
            + "\tpublic static final String VERSION = \"${escapeJava(versionString)}\";\n"
            + "\tpublic static final int GIT_REVISION = ${gitRevision};\n"
            + "\tpublic static final String GIT_SHA = \"${escapeJava(gitSha)}\";\n"
            + "\tpublic static final String GIT_DATE = \"${escapeJava(gitDate)}\";\n"
            + "\tpublic static final String GIT_BRANCH = \"${escapeJava(gitBranch)}\";\n"
            + "\tpublic static final String BUILD_DATE = \"${escapeJava(buildDate)}\";\n"
            + "\tpublic static final long BUILD_UNIX_TIME = ${nowMillis}L;\n"
            + "\tpublic static final int DIRTY = ${dirty};\n\n"
            + "\tprivate ${cls}(){}\n"
            + "}\n"
        )
    }
}
