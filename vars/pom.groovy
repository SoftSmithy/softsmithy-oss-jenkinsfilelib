def call(String mavenVersion, String javaVersion){
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
            string(name:"versionDigitToIncrement",
                    description:"Which digit to increment. Starts from zero.",
                    defaultValue: "1")
        }
        stages {
            stage ('Initialize') {
                steps {
                    sh '''
                    echo "PATH = ${PATH}"
                    echo "M2_HOME = ${M2_HOME}"
                '''
                }
            }

            stage ('Build non-master branches') {
                when {
                    not {
                        branch 'master'
                    }
                }
                steps {
                    sh 'mvn clean install -DperformRelease=true'
                }
            }

            stage ('Build & deploy master branch') {
                when {
                    branch 'master'
                    not { expression { params.RELEASE } }
                }
                steps {
                    sh 'mvn clean deploy -DperformRelease=true'
                }
            }

            stage ('Release') {
                when {
                    branch 'master'
                    expression { params.RELEASE }
                }
                steps {
//                 release {
                    sh 'mvn -B -e gitflow:release-start -DperformRelease=true -DversionDigitToIncrement=${versionDigitToIncrement}'
                    sh 'mvn -B -e gitflow:release-finish -DperformRelease=true -DversionDigitToIncrement=${versionDigitToIncrement} -DpostReleaseGoals="deploy -DperformRelease=true" -Dverbose=true'
//                 }
                }
            }
        }

    }
}