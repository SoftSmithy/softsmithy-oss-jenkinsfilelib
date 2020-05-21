def call(ProjectType projectType, String mavenVersion, String javaVersion) {
    def mavenArgs = projectType.isContainingJavaSourceFiles() ? "-Dmaven.test.failure.ignore=true" : "";

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
            string(name: "versionDigitToIncrement",
                    description: "Which digit to increment. Starts from zero.",
                    defaultValue: "1")
        }
        stages {
            stage('Initialize') {
                steps {
                    sh '''
                    echo "PATH = ${PATH}"
                    echo "M2_HOME = ${M2_HOME}"
                '''
                }
            }

            stage('Build non-master branches') {
                when {
                    not {
                        branch 'master'
                    }
                }
                steps {
                    sh 'mvn -Dmaven.test.failure.ignore=true clean install -DperformRelease=true'
                }
                post {
                    success {
                        if (projectType.isContainingJavaSourceFiles()) {
                            junit allowEmptyResults: true, 'target/surefire-reports/**/*.xml'
                        }
                    }
                }
            }

            stage('Build & deploy master branch') {
                when {
                    branch 'master'
                    not { expression { params.RELEASE } }
                }
                steps {
                    sh 'mvn -Dmaven.test.failure.ignore=true clean deploy -DperformRelease=true'
                }
                post {
                    success {
                        if (projectType.isContainingJavaSourceFiles()) {
                            junit allowEmptyResults: true, 'target/surefire-reports/**/*.xml'
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
                    sh 'mvn -B -e -Dmaven.test.failure.ignore=true gitflow:release-start -DperformRelease=true -DversionDigitToIncrement=${versionDigitToIncrement}'
                    sh 'mvn -B -e -Dmaven.test.failure.ignore=true gitflow:release-finish -DperformRelease=true -DversionDigitToIncrement=${versionDigitToIncrement} -DpostReleaseGoals="deploy -DperformRelease=true" -Dverbose=true'
//                 }
                }
            }
        }

    }

}