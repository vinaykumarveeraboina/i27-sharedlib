import com.i27academy.builds.Javabuild

def call(Map pipelineParams) {
    Javabuild build = new Javabuild(this)

    pipeline {
        agent {
            label 'k8s-slave'
        }
        parameters {
            choice(name: 'buildOnly', choices: 'NO\nYES', description: "This will only build the application")
            choice(name: 'scanOnly', choices: 'NO\nYES', description: "This will only SCAN the application")
            choice(name: 'dockerpush', choices: 'NO\nYES', description: "This will build the application, Docker Build, and Docker push")
            choice(name: 'deployToDev', choices: 'NO\nYES', description: "This will deploy the app to Dev")
            choice(name: 'deployToTest', choices: 'NO\nYES', description: "This will deploy the app to Test")
            choice(name: 'deployToStage', choices: 'NO\nYES', description: "This will deploy the app to Stage")
        }
        options {
            buildDiscarder(logRotator(daysToKeepStr: '7', numToKeepStr: '5'))
        }
        environment {
            DOCKERHUB = "docker.io/vinaykumarveeraboina"
            APPLICATION_NAME = 'eureka'
            POM_VERSION = readMavenPom().getVersion()
            POM_PACKAGING = readMavenPom().getPackaging()
            DOCKER_CREDS = credentials('DockerHub')
            SONAR_URL = 'http://20.6.130.89:9000'
            SONAR_TOKEN = credentials('sonar')
        }
        tools {
            maven 'maven-3.8.8'
            jdk 'Jdk17'
        }
        stages {
            stage('Build') {
                when {
                    anyOf {
                        expression { params.buildOnly == 'YES' }
                        expression { params.dockerpush == 'YES' }
                    }
                }
                steps {
                    script {
                        build.applicationBuild(env.APPLICATION_NAME)
                    }
                }
            }
            stage('Unit-Test') {
                when {
                    anyOf {
                        expression { params.buildOnly == 'YES' }
                        expression { params.dockerpush == 'YES' }
                    }
                }
                steps {
                    echo "Testing the ${env.APPLICATION_NAME} application"
                    sh "mvn test"
                }
                post {
                    always {
                        junit 'target/surefire-reports/*.xml'
                    }
                }
            }
            stage('Sonar_Test') {
                when {
                    anyOf {
                        expression { params.scanOnly == 'YES' }
                    }
                }
                steps {
                    echo " ************************* STARTING SONAR ANALYSIS with Quality gate ************************"
                    withSonarQubeEnv('SonarQube') {
                        sh """
                        mvn clean verify sonar:sonar \
                         -Dsonar.projectKey=i127-eureka \
                         -Dsonar.host.url=${env.SONAR_URL} \
                         -Dsonar.login=${env.SONAR_TOKEN}
                         """
                    }
                    timeout(time: 5, unit: 'MINUTES') {
                        waitForQualityGate abortPipeline: true
                    }
                }
            }
            stage('Docker Build and Push') {
                when {
                    anyOf {
                        expression { params.dockerpush == 'YES' }
                    }
                }
                steps {
                    script {
                        build.dockerBuildPush()
                    }
                }
            }
            stage('Docker deploy to DEV') {
                when {
                    anyOf {
                        expression { params.deployToDev == 'YES' }
                    }
                }
                steps {
                    script {
                        build.imagevalidation()
                        build.DockerDeploy('dev', '5761', '8761')
                    }
                }
            }
            stage('Docker deploy to TEST env') {
                when {
                    anyOf {
                        expression { params.deployToTest == 'YES' }
                    }
                }
                steps {
                    script {
                        build.imagevalidation()
                        build.DockerDeploy('test', '6761', '8761')
                    }
                }
            }
            stage('Docker deploy to STAGE env') {
                when {
                    allOf{
                        anyOf {
                            expression { params.deployToStage == 'YES' }
                        }
                        anyOf {
                             branch 'release/*' 
                        }
                    }
                }
                steps {
                   timeout(time: 300, unit: 'SECONDS') {
                       input message: " Deploying ${env.APPLICATION_NAME} to stage??? ", ok: 'YES', submitter: 'owner'
                   }
                    script {
                        build.imagevalidation()
                        build.DockerDeploy('stage', '7761', '8761')
                    }
                }
            }
            stage ('Clean Workspace') {
              steps {
                cleanWs()
              }
            }
        }
    }
}
