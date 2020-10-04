def call(ProjectType projectType, String mavenVersion, String javaVersion) {
    def mavenArgs = "-e -B -Dmaven.test.failure.ignore=true -DperformRelease=true"

    pipeline {
        agent any
        triggers {
            pollSCM('H/10 * * * *')
        }
        tools {
            maven mavenVersion
            jdk javaVersion
        }
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
                        anyOf{
                            branch 'master'
                            branch pattern: "support\\/release-\\d+.*", comparator: "REGEXP"
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
                                junit allowEmptyResults: true, testResults: 'target/surefire-reports/**/*.xml'
                            }
                        }
                    }
                }
            }

            stage('Build & deploy master & support branch') {
                when {
                    anyOf{
                        branch 'master'
                        branch pattern: "support\\/release-\\d+.*", comparator: "REGEXP"
                    }
                    not { expression { params.RELEASE } }
                }
                steps {
                    sh "mvn ${mavenArgs} clean deploy"
                }
                post {
                    success {
                        script {
                            if (projectType.isContainingJavaSourceFiles()) {
                                junit allowEmptyResults: true, testResults: 'target/surefire-reports/**/*.xml'
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
                    sh "mvn ${mavenArgs} gitflow:release-start -DversionDigitToIncrement=${versionDigitToIncrement}"
                    sh "mvn ${mavenArgs} gitflow:release-finish -DversionDigitToIncrement=${versionDigitToIncrement} -DpostReleaseGoals=\"deploy ${mavenArgs} -Dverbose=true\""
//                 }
                }
            }

            stage('Hotfix') {
                when {
                    branch pattern: "support\\/release-\\d+.*", comparator: "REGEXP"
                    expression { params.HOTFIX }
                }
                steps {
//                 release {
                    sh "mvn ${mavenArgs} gitflow:hotfix-start -DfromBranch=${hotfixFromBranch} -DhotfixVersion=${hotfixVersion}"
                    sh "mvn ${mavenArgs} gitflow:hotfix-finish -DhotfixVersion=${hotfixVersion} -DpostHotfixGoals=\"deploy ${mavenArgs} -Dverbose=true\""
//                 }
                }
            }
        }

    }

}