 def call(String slnName, String pckgName, String tstProjectName) {

   def doSonarScan = false;

   def solutionName = slnName;
   def packageName = pckgName;
   def testProjectName = tstProjectName;

   echo "Solution ${solutionName}"

   pipeline {

     agent any

     options {

       buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '5', daysToKeepStr: '', numToKeepStr: '5')
       skipDefaultCheckout(true) // This option is added to skip default checkout
     }

     stages {

       stage('SonarQube Scan?') {

         steps {
           script {
             try {
               timeout(time: 60, unit: 'SECONDS') {
                 doSonarScan = input(id: 'chkSonarScan', message: 'Was this successful?',
                   parameters: [
                     [$class: 'BooleanParameterDefinition', defaultValue: doSonarScan, description: '', name: 'Please confirm by ticking if you woult like to do SonarQube Scan']
                   ])
               }
             } catch (error) {
               echo 'skipping sonar'
             }
           }
         }

       }

       stage('Checking out repository') {
         steps {
           echo "[${new Date().format('HH:mm:ss')}] Deleting workspace"
           deleteDir()

           checkout scm

         }
       }

       stage('Restoring Packages') {

         steps {
           echo "Package restore started using Nuget.exe ${solutionName}"
           bat "dotnet restore ${solutionName}"

         }

       }

       stage('Build') {
         steps {
           echo "Building ${env.JOB_NAME}..."
           bat "\"C:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\MSBuild\\Current\\Bin\\MSBuild.exe\" ${solutionName} /t:restore /p:RestorePackagesConfig=true /p:Configuration=Release"
           bat "\"C:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\MSBuild\\Current\\Bin\\MSBuild.exe\" ${solutionName} /p:Configuration=Release /p:Platform=\"Any CPU\" /p:PublishDir=\"${WORKSPACE}\\Source\\${packageName}\\bin\\Publish\\"
         }
       }

       stage('SonarQube Analysis') {
         when {
           expression {
             doSonarScan == true
           }
         }
         steps {
           script {
             def msbuildHome = "C:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\MSBuild\\Current\\Bin\\"
             def scannerHome = tool 'SonarScanner for MSBuild'
             withSonarQubeEnv() {
               bat "\"${scannerHome}\\SonarScanner.MSBuild.exe\" begin /k:\"${packageName}\""
               bat "\"${msbuildHome}\\MSBuild.exe\" /t:Rebuild "
               bat "\"${scannerHome}\\SonarScanner.MSBuild.exe\" end"
             }
           }
         }
       }

      // stage('Run Unit Tests') {
        // steps {
        //   script {
         //    echo "Unit tests start"
         //    bat " \"F:\\Tools\\opencover.4.7.1221\\OpenCover.Console.exe\" -target:\"F:\\Tools\\NUnit.ConsoleRunner.3.17.0\\tools\\nunit3-console.exe\" -targetargs:\"test\\${testProjectName}\\bin\\release\\${testProjectName}.dll\" -output:TestResult.xml"
         //  }
       //  }
      // }

       stage('Create and Push NuGet Package') {
         steps {
           script {
             // Path to your .nuspec file
		
             def ProjectName = "${packageName}"
             echo "Create nuget package step"
             // Path to your PowerShell script
             def psScriptPath = 'C:\Tools\commonbuild\\NugetPackagePublish.ps1'

             //print the nuget version that is about get published	
             if (env.BRANCH_NAME == 'master') {
               nugetVersion = "0.0.0-beta${env.BUILD_NUMBER}"
             } else {
               def branchName = env.BRANCH_NAME.replace('/', '-').replace('_', '').replace('#', '') // Replace / with - in branch name
               nugetVersion = "0.0.0-alpha${env.BUILD_NUMBER}-${branchName}"
             }
             nugetVersion = nugetVersion.substring(0, Math.min(nugetVersion.length(), 20))

             echo "Nuget version getting created: ${nugetVersion}"

             // Execute PowerShell script with nuspec file path as parameter
             powershell(returnStdout: true, script: "powershell.exe -NonInteractive -ExecutionPolicy Bypass -File ${psScriptPath} -ProjectName ${ProjectName} -BranchName ${env.BRANCH_NAME} -BuildNumber ${env.BUILD_NUMBER} ")
           }
         }
       }

     }
     post {
       always {
         // Clean after build
         echo "Cleaning workspace"
         deleteDir()
       }
     }

   }
 }