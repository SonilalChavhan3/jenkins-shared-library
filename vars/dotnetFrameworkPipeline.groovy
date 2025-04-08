@Library("jenkins-shared-library") _

def call(String slnName, String pckgName, String tstProjectName) {

    def doSonarScan = false

    def solutionName = slnName
    def packageName = pckgName
    def testProjectName = tstProjectName

    echo "Solution: ${solutionName}"

    pipeline {
        agent any

        options {
            buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '5', daysToKeepStr: '', numToKeepStr: '5')
            skipDefaultCheckout(true)
        }

        stages {

            stage('SonarQube Scan?') {
                steps {
                    script {
                        try {
                            timeout(time: 60, unit: 'SECONDS') {
                                doSonarScan = input(
                                    id: 'chkSonarScan',
                                    message: 'Was this successful?',
                                    parameters: [
                                        [$class: 'BooleanParameterDefinition',
                                         defaultValue: doSonarScan,
                                         description: '',
                                         name: 'Please confirm by ticking if you would like to do SonarQube Scan']
                                    ]
                                )
                            }
                        } catch (error) {
                            echo 'Skipping SonarQube Scan'
                        }
                    }
                }
            }

            stage('Checkout Code') {
                steps {
                    echo "[${new Date().format('HH:mm:ss')}] Cleaning workspace"
                    deleteDir()
                    checkout scm
                }
            }

            stage('Restore Packages') {
                steps {
                    echo "Restoring packages for solution ${solutionName}"
                    bat "dotnet restore ${solutionName}"
                }
            }

            stage('Build Project') {
                steps {
                    echo "Building ${solutionName}"
                    bat "dotnet build ${solutionName} -c Release"
                }
            }

            stage('SonarQube Analysis') {
                when {
                    expression { doSonarScan == true }
                }
                steps {
                    script {
                        def scannerHome = tool 'SonarScanner for MSBuild'
                        withSonarQubeEnv() {
                            bat "\"${scannerHome}\\SonarScanner.MSBuild.exe\" begin /k:\"${packageName}\""
                            bat "dotnet build ${solutionName} -c Release"
                            bat "\"${scannerHome}\\SonarScanner.MSBuild.exe\" end"
                        }
                    }
                }
            }

            // Uncomment this stage if you want to run tests
            // stage('Run Unit Tests') {
            //     steps {
            //         echo "Running unit tests"
            //         bat "dotnet test test\\${testProjectName} -c Release --no-build --logger:trx"
            //     }
            // }

            stage('Publish Project') {
                steps {
                    echo "Publishing ${packageName}"
                    bat "dotnet publish source\\${packageName}\\${packageName}.csproj -c Release -o ${WORKSPACE}\\bin\\publish"
                }
            }

            stage('Create & Push NuGet Package') {
                steps {
                    script {
                        def ProjectName = "${packageName}"
                        echo "Creating NuGet package for ${ProjectName}"

                        def psScriptPath = 'C:\\Tools\\commonbuild\\NugetPackagePublish.ps1'

                        def branchName = env.BRANCH_NAME.replace('/', '-').replace('_', '').replace('#', '')
                        def nugetVersion = env.BRANCH_NAME == 'master' ?
                            "0.0.0-beta${env.BUILD_NUMBER}" :
                            "0.0.0-alpha${env.BUILD_NUMBER}-${branchName}"

                        nugetVersion = nugetVersion.take(64)
                        echo "NuGet version getting created: ${nugetVersion}"

                        powershell(returnStdout: true,
                            script: "powershell.exe -NonInteractive -ExecutionPolicy Bypass -File ${psScriptPath} -ProjectName ${ProjectName} -BranchName ${env.BRANCH_NAME} -BuildNumber ${env.BUILD_NUMBER}")
                    }
                }
            }
        }

        post {
            always {
                echo "Cleaning workspace"
                deleteDir()
            }
        }
    }
}
