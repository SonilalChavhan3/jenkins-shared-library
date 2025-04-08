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

            // Uncomment if test project is needed
            // stage('Run Unit Tests') {
            //     steps {
            //         echo "Running unit tests"
            //         bat "dotnet test test\\${testProjectName} -c Release --no-build --logger:trx"
            //     }
            // }

            stage('Publish Project') {
                steps {
                    echo "Publishing ${packageName}"
                    bat "dotnet publish ${packageName}\\${packageName}.csproj -c Release -o ${WORKSPACE}\\Source\\${packageName}\\bin\\Publish\\"
                    bat "dir /s /b *.csproj"
                }
            }

            stage('Create & Push NuGet Package') {
                steps {
                    script {
                        def branchName = env.BRANCH_NAME.replace('/', '-')
                        def nugetVersion = env.BRANCH_NAME == 'master'
                            ? "1.0.0-beta${env.BUILD_NUMBER}"
                            : "1.0.0-alpha${env.BUILD_NUMBER}-${branchName}"

                        // Pack
                        bat """
                            dotnet pack ${packageName}\\${packageName}.csproj ^
                                -c Release ^
                                -p:IsPackable=true ^
                                -p:PackageVersion=${nugetVersion} ^
                                -o ${WORKSPACE}\\${packageName}\\bin\\nuget
                        """

                       // Push
bat """
    dotnet nuget push ${WORKSPACE}\\${packageName}\\bin\\nuget\\*.nupkg ^
        --source batch-24 ^
        --configfile NuGet.Config ^
        --skip-duplicate
"""
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
