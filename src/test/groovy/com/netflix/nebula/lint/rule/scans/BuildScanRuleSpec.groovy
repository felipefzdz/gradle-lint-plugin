package com.netflix.nebula.lint.rule.scans

import com.netflix.nebula.lint.rule.test.AbstractRuleSpec
import com.netflix.nebula.lint.rule.wrapper.WrapperProjectBuilder
import org.gradle.util.GradleVersion

class BuildScanRuleSpec extends AbstractRuleSpec {

    def 'missing build-scan plugin using the old style of including plugins is recorded as a violation'() {
        setup:
        project.buildFile << """
           apply plugin: 'nebula.lint'
        """

        def rule = new BuildScanRule()
        rule.project = project

        when:
        def results = runRulesAgainst(rule)

        then:
        results.violates(BuildScanRule)
    }

    def 'missing build-scan plugin using the new style of including plugins is recorded as a violation'() {
        setup:
        project.buildFile << """
            plugins {
                id 'nebula.lint' version '6.1.4'
            }
        """

        def rule = new BuildScanRule()
        rule.project = project

        when:
        def results = runRulesAgainst(rule)

        then:
        results.violates(BuildScanRule)
    }

    def 'existing build-scan plugin using the old style of including plugins is not recorded as a violation'() {
        setup:
        project.buildFile << """
           apply plugin: 'com.gradle.build-scan'
        """

        def rule = new BuildScanRule()
        rule.project = project

        when:
        def results = runRulesAgainst(rule)

        then:
        !results.violates(BuildScanRule)
    }

    def 'existing build-scan plugin using the new style of including plugins is not recorded as a violation'() {
        setup:
        project.buildFile << """
            plugins {
                id 'com.gradle.build-scan' version '1.4'
            }
        """

        def rule = new BuildScanRule()
        rule.project = project

        when:
        def results = runRulesAgainst(rule)

        then:
        !results.violates(BuildScanRule)
    }

    def 'autofix using the old style of including plugins when no dependencies block was found'() {
        setup:
        project.buildFile << """
            buildscript {
                repositories {
                    jcenter()
                }
            }
            apply plugin: 'java'
        """.stripIndent()

        def rule = new BuildScanRule()
        rule.project = project

        when:
        def correctedScript = correct(rule)

        then:
        correctedScript == """
            buildscript {
                repositories {
                    jcenter()
                }
                dependencies {
                    classpath 'com.gradle.scans.lint:rules:1.4'
                }
            }
            apply plugin: 'com.gradle.build-scan'
            apply plugin: 'java'
            
            //buildScan {
            //    licenseAgreementUrl = 'https://gradle.com/terms-of-service'
            //    licenseAgree = 'yes'
            //}
            """.stripIndent()
    }

    def 'autofix using the old style of including plugins when no dependencies block was found inside of the buildscript block'() {
        setup:
        project.buildFile << """
            buildscript {
                repositories {
                    jcenter()
                }
            }
            apply plugin: 'java'
            
            dependencies {
                testCompile ('junit:junit:4.12')
            }
        """.stripIndent()

        def rule = new BuildScanRule()
        rule.project = project

        when:
        def correctedScript = correct(rule)

        then:
        correctedScript == """
            buildscript {
                repositories {
                    jcenter()
                }
                dependencies {
                    classpath 'com.gradle.scans.lint:rules:1.4'
                }
            }
            apply plugin: 'com.gradle.build-scan'
            apply plugin: 'java'
            
            //buildScan {
            //    licenseAgreementUrl = 'https://gradle.com/terms-of-service'
            //    licenseAgree = 'yes'
            //}
            
            dependencies {
                testCompile ('junit:junit:4.12')
            }
            """.stripIndent()
    }

    def 'autofix using the old style of including plugins when the dependencies block was found'() {
        setup:
        project.buildFile << """
            buildscript {
                repositories {
                    jcenter()
                }
                dependencies {
                    classpath "com.netflix.nebula:gradle-lint-plugin:5.1.2"
                }
            }
            apply plugin: 'nebula.lint'
        """.stripIndent()

        def rule = new BuildScanRule()
        rule.project = project

        when:
        def correctedScript = correct(rule)

        then:
        correctedScript == """
            buildscript {
                repositories {
                    jcenter()
                }
                dependencies {
                    classpath 'com.gradle.scans.lint:rules:1.4'
                    classpath "com.netflix.nebula:gradle-lint-plugin:5.1.2"
                }
            }
            apply plugin: 'com.gradle.build-scan'
            apply plugin: 'nebula.lint'
            
            //buildScan {
            //    licenseAgreementUrl = 'https://gradle.com/terms-of-service'
            //    licenseAgree = 'yes'
            //}
        """.stripIndent()
    }

    def 'autofix using the new style of including plugins when the plugins block was found'() {
        setup:
        project.buildFile << """
            plugins {
                id 'nebula.lint' version '6.1.4'
            }
        """.stripIndent()

        def rule = new BuildScanRule()
        rule.project = project

        when:
        def correctedScript = correct(rule)

        then:
        correctedScript == """
            plugins {
                id 'com.gradle.build-scan' version '1.4'
                id 'nebula.lint' version '6.1.4'
            }
            
            //buildScan {
            //    licenseAgreementUrl = 'https://gradle.com/terms-of-service'
            //    licenseAgree = 'yes'
            //}
        """.stripIndent()
    }

    def 'autofix using the new style of including plugins when no plugins or buildscript block were found and gradle version is 2.1+'() {
        setup:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.buildFile << ""
        project.gradle.version = GradleVersion.version('3.2')

        def rule = new BuildScanRule()
        rule.project = project

        when:
        def correctedScript = correct(rule)

        then:
        correctedScript == """plugins {
    id 'com.gradle.build-scan' version '1.4'
}

//buildScan {
//    licenseAgreementUrl = 'https://gradle.com/terms-of-service'
//    licenseAgree = 'yes'
//}"""
    }

    def 'autofix using the new style of including plugins when no plugins block was found and buildscript block was found and gradle version is 2.1+'() {
        setup:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.buildFile << """
            buildscript {
                repositories {
                    jcenter()
                }
            }
        """.stripIndent()
        project.gradle.version = GradleVersion.version('3.2')

        def rule = new BuildScanRule()
        rule.project = project

        when:
        def correctedScript = correct(rule)

        then:
        correctedScript == """
            buildscript {
                repositories {
                    jcenter()
                }
            }
            plugins {
                id 'com.gradle.build-scan' version '1.4'
            }
            
            //buildScan {
            //    licenseAgreementUrl = 'https://gradle.com/terms-of-service'
            //    licenseAgree = 'yes'
            //}
        """.stripIndent()
    }

    def 'autofix using the old style of including plugins when no apply plugin statements were found and gradle version is 2.0-'() {
        setup:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.buildFile << """
            buildscript {
                repositories {
                    jcenter()
                }
            }
        """.stripIndent()
        project.gradle.version = GradleVersion.version('2.0')

        def rule = new BuildScanRule()
        rule.project = project

        when:
        def correctedScript = correct(rule)

        then:
        correctedScript == """
            buildscript {
                repositories {
                    jcenter()
                }
                dependencies {
                    classpath 'com.gradle.scans.lint:rules:1.4'
                }
            }
            apply plugin: 'com.gradle.build-scan'
            
            //buildScan {
            //    licenseAgreementUrl = 'https://gradle.com/terms-of-service'
            //    licenseAgree = 'yes'
            //}
        """.stripIndent()
    }
}
