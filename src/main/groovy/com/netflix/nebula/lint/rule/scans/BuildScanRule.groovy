package com.netflix.nebula.lint.rule.scans

import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradlePlugin
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.util.GradleVersion


class BuildScanRule extends GradleLintRule {
    String description = 'build-scan plugin should be applied'
    private final String licenseAgreement = """
//buildScan {
//    licenseAgreementUrl = 'https://gradle.com/terms-of-service'
//    licenseAgree = 'yes'
//}"""

    @Override
    void visitApplyPlugin(MethodCallExpression call, String plugin) {
        if (plugin == 'com.gradle.build-scan') {
            bookmark('buildScanFoundOnApplyPlugin', call)
        }
        if (!bookmark('firstApplyPlugin')) {
            bookmark('firstApplyPlugin', call)
        }
        bookmark('lastApplyPlugin', call)
    }

    @Override
    void visitGradlePlugin(MethodCallExpression call, String conf, GradlePlugin plugin) {
        if (plugin.id == 'com.gradle.build-scan') {
            bookmark('buildScanFoundOnPluginsBlock', call)
        }
        if (!bookmark('firstPluginInPluginsBlock')) {
            bookmark('firstPluginInPluginsBlock', call)
        }
    }

    @Override
    void visitPlugins(MethodCallExpression call) {
        bookmark('plugins', call)
    }

    @Override
    void visitBuildscript(MethodCallExpression call) {
        bookmark('buildscript', call)
    }

    @Override
    void visitDependencies(MethodCallExpression call) {
        bookmark('dependencies', call)
    }

    @Override
    void visitRepositories(MethodCallExpression call) {
        bookmark('repositories', call)
    }

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        if (dep.toNotation() == 'com.gradle:build-scan-plugin:1.4') {
            bookmark('buildScanDependencyFoundOnBuildScript')
        }
        if (conf == 'classpath' && !bookmark('firstDependencyInBuildscriptBlock')) {
            bookmark('firstDependencyInBuildscriptBlock', call)
        }
    }

    @Override
    protected void visitClassComplete(ClassNode node) {
        if (buildScanPluginNotPresent()) {
            def violation = addBuildLintViolation("""build-scan plugin is not applied. Go to: https://scans.gradle.com/get-started""")
            prepareAutofixFor(violation)
        }
    }

    def buildScanPluginNotPresent() {
        !(bookmark('buildScanFoundOnPluginsBlock') || bookmark('buildScanFoundOnApplyPlugin'))
    }

    def prepareAutofixFor(GradleViolation violation) {
        def buildscript = bookmark('buildscript')
        def firstPluginInPluginsBlock = bookmark('firstPluginInPluginsBlock')
        def firstApplyPlugin = bookmark('firstApplyPlugin')
        if (firstPluginInPluginsBlock) {
            fixExistingPluginsBlock(firstPluginInPluginsBlock, violation)
        } else if (firstApplyPlugin) {
            fixExistingApplyPluginsSection(firstApplyPlugin, buildscript, violation)
        } else {
            fixScriptWithNoPluginReferences(buildscript, violation)
        }
    }

    def fixExistingPluginsBlock(ASTNode firstPluginInPluginsBlock, GradleViolation violation) {
        def plugin = ''.padRight(firstPluginInPluginsBlock.columnNumber - 1) + "id 'com.gradle.build-scan' version '1.4'"
        violation.insertBefore(firstPluginInPluginsBlock, plugin)
        violation.insertAfter(bookmark('plugins'), licenseAgreement)
    }

    def fixExistingApplyPluginsSection(ASTNode firstApplyPlugin, ASTNode buildscript, GradleViolation violation) {
        fixBuildscriptBlock(buildscript, violation)
        violation.insertBefore(firstApplyPlugin, "apply plugin: 'com.gradle.build-scan'")
        violation.insertAfter(bookmark('lastApplyPlugin'), licenseAgreement)
    }

    def fixScriptWithNoPluginReferences(ASTNode buildscript, GradleViolation violation) {
        if (GradleVersion.version(project.gradle.getGradleVersion()) > GradleVersion.version("2.1")) {
            def plugins = "plugins {\n    id 'com.gradle.build-scan' version '1.4'\n}"
            if (buildscript) {
                violation.insertAfter(buildscript, plugins)
                violation.insertAfter(buildscript, licenseAgreement)
            } else {
                violation.insertAfter(buildFile, 0, "$plugins\n$licenseAgreement")
            }
        } else {
            fixBuildscriptBlock(buildscript, violation)
            violation.insertAfter(buildscript, "apply plugin: 'com.gradle.build-scan'")
            violation.insertAfter(buildscript, licenseAgreement)
        }
    }

    def fixBuildscriptBlock(ASTNode buildscript, GradleViolation violation) {
        if (buildscript) {
            def firstDependencyInBuildscriptBlock = bookmark('firstDependencyInBuildscriptBlock')
            if (!bookmark('dependencies') || !firstDependencyInBuildscriptBlock) {
                if (!bookmark('buildScanDependencyFoundOnBuildScript')) {
                    def repositories = bookmark('repositories')
                    def dependencies = "dependencies {\n    classpath 'com.gradle.scans.lint:rules:1.4'\n}"
                    def formattedDependencies = dependencies.stripIndent()
                            .split('\n')
                            .collect { line -> ''.padRight(repositories.columnNumber - 1) + line }
                            .join('\n')
                    violation.insertAfter(repositories, formattedDependencies)
                }
            } else {
                def dependency = ''.padRight(firstDependencyInBuildscriptBlock.columnNumber - 1) + "classpath 'com.gradle.scans.lint:rules:1.4'"
                violation.insertBefore(firstDependencyInBuildscriptBlock, dependency)
            }
        }
    }
}
