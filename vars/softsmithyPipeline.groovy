import org.softsmithy.jenkinsfilelib.config.BuildToolType
import org.softsmithy.jenkinsfilelib.config.ProgrammingLanguageType

def call() {
    def mavenArgs = "-e -B -Dmaven.test.failure.ignore=true -DperformRelease=true"
    def supportBranchPattern = "support\\/release-\\d+.*"
    def junitTestResults = 'target/surefire-reports/**/*.xml'
    def config = readJSON file: 'jenkinsPipelineSoftsmithyConfig.json'
    def projectType = config.projectType
    def buildTool = config.buildTool
    def programmingLanguage = config.programmingLanguage

    pipeline {
        agent any
        triggers {
            pollSCM('H/10 * * * *')
        }
        tools {
            script {
                if (buildTool.type == BuildToolType.MAVEN) {
                    maven buildTool.version
                }
                if (programmingLanguage.type == ProgrammingLanguageType.JAVA) {
                    jdk programmingLanguage.version
                }
            }
        }
//        environment {
//            AWS_ACCESS_KEY_ID     = credentials('jenkins-aws-secret-key-id')
//            AWS_SECRET_ACCESS_KEY = credentials('jenkins-aws-secret-access-key')
//        }
//     options {
//         timestamps()
//         ansiColor("xterm")
//     }
        parameters {
            booleanParam(name: "RELEASE",
                    description: "Build a release from current commit.",
                    defaultValue: false)
            booleanParam(name: "HOTFIX",
                    description: "Build a hotfix from current commit.",
                    defaultValue: false)
            string(name: "versionDigitToIncrement",
                    description: "Which digit to increment. Starts from zero. (For hotfix use: hotfixVersion)",
                    defaultValue: "1")
            string(name: "hotfixFromBranch",
                    description: "Branch to start hotfix (master or one of the support branches).",
                    defaultValue: "support/release-")
            string(name: "hotfixVersion",
                    description: "The hotfix version.",
                    defaultValue: "")
        }
        stages {
            stage('Initialize') {
                steps {
                    sh '''
                    echo "PATH = ${PATH}"
                    echo 'Java version: '
                    java -version
                    echo "M2_HOME = ${M2_HOME}"
                    echo 'Maven version: '
                    mvn -v
                '''
                }
            }

            stage('Build non-master/-support branches') {
                when {
                    not {
                        anyOf {
                            branch 'master'
                            branch pattern: "${supportBranchPattern}", comparator: "REGEXP"
                        }
                    }
                }
                steps {
                    sh "mvn ${mavenArgs} clean install"
                }
                post {
                    success {
                        script {
                            if (projectType.isContainingJavaSourceFiles()) {
                                junit allowEmptyResults: true, testResults: "${junitTestResults}"
                            }
                        }
                    }
                }
            }

            stage('Build & deploy master & support branch') {
                when {
                    anyOf {
                        branch 'master'
                        branch pattern: "${supportBranchPattern}", comparator: "REGEXP"
                    }
                    not {
                        anyOf {
                            expression { params.RELEASE }
                            expression { params.HOTFIX }
                        }
                    }
                }
                steps {
                    sh "mvn ${mavenArgs} clean deploy"
                }
                post {
                    success {
                        script {
                            if (projectType.isContainingJavaSourceFiles()) {
                                junit allowEmptyResults: true, testResults: "${junitTestResults}"
                            }
                        }
                    }
                }
            }

            stage('Release') {
                when {
                    branch 'master'
                    expression { params.RELEASE }
                }
                steps {
//                 release {
                    sh "mvn ${mavenArgs} gitflow:release-start -DversionDigitToIncrement=${versionDigitToIncrement} -Dverbose=true"
                    sh "mvn ${mavenArgs} gitflow:release-finish -DversionDigitToIncrement=${versionDigitToIncrement} -Dverbose=true '-DpostReleaseGoals=${mavenArgs} -DskipTests deploy'"
//                 }
                }
            }

            stage('Hotfix') {
                when {
                    branch pattern: "${supportBranchPattern}", comparator: "REGEXP"
                    expression { params.HOTFIX }
                }
                steps {
//                 release {
                    sh "mvn ${mavenArgs} gitflow:hotfix-start -DfromBranch=${hotfixFromBranch} -DhotfixVersion=${hotfixVersion} -Dverbose=true"
                    sh "mvn ${mavenArgs} gitflow:hotfix-finish -DhotfixVersion=${hotfixFromBranch}/${hotfixVersion} -DfetchRemote=false -Dverbose=true '-DpostHotfixGoals=${mavenArgs} -DskipTests deploy'"
                    sh "mvn ${mavenArgs} versions:set -DnextSnapshot=true -DoldVersion=\'*\' -DgroupId=\'*\' -DartifactId=\'*\' -DprocessAllModules=true"
                    sh "mvn ${mavenArgs} versions:commit"
                    sh "git add ."
                    sh "git commit -m'next SNAPSHOT version'"
                    sh "git push"
//                 }
                }
            }
        }

    }

}