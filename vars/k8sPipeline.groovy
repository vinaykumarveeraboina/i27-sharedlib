import com.i27academy.builds.Javabuild
import com.i27academy.k8s.K8s

def call(Map pipelineParams) {
    Javabuild build = new Javabuild(this)
    K8s k8s = new K8s(this)

    pipeline {
        agent {
            label 'k8s-slave'
        }
        parameters {
            choice(name: 'k8Login', choices: 'NO\nYES', description: "This will log in to Kubernetes")
            choice(name: 'buildOnly', choices: 'NO\nYES', description: "This will only build the application")
            choice(name: 'scanOnly', choices: 'NO\nYES', description: "This will only SCAN the application")
            choice(name: 'dockerpush', choices: 'NO\nYES', description: "This will build, Dockerize, and push the app")
            choice(name: 'deployToDev', choices: 'NO\nYES', description: "This will deploy the app to Dev")
            choice(name: 'deployToTest', choices: 'NO\nYES', description: "This will deploy the app to Test")
            choice(name: 'deployToStage', choices: 'NO\nYES', description: "This will deploy the app to Stage")
        }
        options {
            buildDiscarder(logRotator(daysToKeepStr: '7', numToKeepStr: '5'))
        }
        environment {
            DOCKERHUB = "docker.io/vinaykumarveeraboina"
            APPLICATION_NAME = "${pipelineParams.appName}"
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
            stage('Authenticate to AKS') {
                when {
                    expression { params.k8Login == 'YES' }
                }
                steps {
                    echo "Authenticating with AKS"
                    script {
                        k8s.akslogin()
                    }
                }
            }
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
                    expression { params.scanOnly == 'YES' }
                }
                steps {
                    echo "Starting SonarQube analysis"
                    withSonarQubeEnv('SonarQube') {
                        sh """
                        mvn clean verify sonar:sonar \
                        -Dsonar.projectKey=i127-eureka \
                        -Dsonar.host.url=${env.SONAR_URL} \
                        -Dsonar.login=${SONAR_TOKEN}
                        """
                    }
                    timeout(time: 5, unit: 'MINUTES') {
                        waitForQualityGate abortPipeline: true
                    }
                }
            }
            stage('Docker Build and Push') {
                when {
                    expression { params.dockerpush == 'YES' }
                }
                steps {
                    script {
                        dockerBuildPush()
                    }
                }
            }
            stage('Docker Deploy to DEV') {
                when {
                    expression { params.deployToDev == 'YES' }
                }
                steps {
                    script {
                        imageValidation(build)
                        DockerDeploy('dev', '5761', '8761')
                    }
                }
            }
            stage('Docker Deploy to TEST') {
                when {
                    expression { params.deployToTest == 'YES' }
                }
                steps {
                    script {
                        imageValidation(build)
                        DockerDeploy('test', '6761', '8761')
                    }
                }
            }
            stage('Docker Deploy to STAGE') {
                when {
                    allOf {
                        expression { params.deployToStage == 'YES' }
                        branch 'release/*'
                    }
                }
                steps {
                    timeout(time: 300, unit: 'SECONDS') {
                        input message: "Deploying ${env.APPLICATION_NAME} to stage?", ok: 'YES', submitter: 'owner'
                    }
                    script {
                        imageValidation(build)
                        DockerDeploy('stage', '7761', '8761')
                    }
                }
            }
            stage('Clean Workspace') {
                steps {
                    cleanWs()
                }
            }
        }
    }
}

// Helper functions
def DockerDeploy(envDeploy, hostPort, containerPort) {
    echo "Deploying to Docker $envDeploy"
    withCredentials([usernamePassword(credentialsId: 'docker_dev_server', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
        try {
            sh """
            sshpass -p ${env.PASSWORD} ssh -o StrictHostKeyChecking=no ${env.USERNAME}@${env.docker_dev_server} docker pull ${env.DOCKERHUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}
            sshpass -p ${env.PASSWORD} ssh -o StrictHostKeyChecking=no ${env.USERNAME}@${env.docker_dev_server} docker stop ${env.APPLICATION_NAME}-$envDeploy || true
            sshpass -p ${env.PASSWORD} ssh -o StrictHostKeyChecking=no ${env.USERNAME}@${env.docker_dev_server} docker rm ${env.APPLICATION_NAME}-$envDeploy || true
            sshpass -p ${env.PASSWORD} ssh -o StrictHostKeyChecking=no ${env.USERNAME}@${env.docker_dev_server} docker run -d -p $hostPort:$containerPort --name ${env.APPLICATION_NAME}-$envDeploy ${env.DOCKERHUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}
            """
        } catch (err) {
            echo "Error during Docker deployment: ${err}"
            throw err
        }
    }
}

def imageValidation(build) {
    try {
        sh "docker pull ${env.DOCKERHUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
    } catch (Exception e) {
        echo "Image not found. Building and pushing the image."
        build.applicationBuild(env.APPLICATION_NAME)
        dockerBuildPush()
    }
}

def dockerBuildPush() {
    sh """
    mkdir -p .cicd
    cp target/i27-${env.APPLICATION_NAME}-${POM_VERSION}.${POM_PACKAGING} .cicd/
    docker build --force-rm --no-cache --pull --rm=true --build-arg JAR_SOURCE=i27-${env.APPLICATION_NAME}-${POM_VERSION}.${POM_PACKAGING} -t ${env.DOCKERHUB}/${env.APPLICATION_NAME}:${GIT_COMMIT} .cicd
    docker login -u ${DOCKER_CREDS_USR} -p ${DOCKER_CREDS_PSW}
    docker push ${env.DOCKERHUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}
    """
}
