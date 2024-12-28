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
            // DOCKERHUB = "docker.io/vinaykumarveeraboina"
            JFROG_REPO = 'i27project.jfrog.io'
            JFROG_REGISTRY = 'boutique-docker'
            APPLICATION_NAME = "${pipelineParams.appName}"
            POM_VERSION = readMavenPom().getVersion()
            POM_PACKAGING = readMavenPom().getPackaging()
            DOCKER_CREDS = credentials('DockerHub')
            JFROG_CREDS = credentials('JFROG_CREDS')
            SONAR_URL = 'http://20.6.130.89:9000'
            SONAR_TOKEN = credentials('sonar')
            AZURE_CLIENT_ID = credentials('azure-client-id')   
            AZURE_CLIENT_SECRET = credentials('azure-client-secret') 
            AZURE_TENANT_ID = credentials('azure-tenant-id')   
            AZURE_SUBSCRIPTION_ID = credentials('azure-subscription-id')
            RESOURCE_GROUP = 'project'
            AKS_CLUSTER_NAME = 'i27project'
            K8S_DEV_FILE = "k8s_dev.yaml"
            K8S_DEV_NAMESPACE = "eureka-dev-ns"
            K8S_TST_NAMESPACE = "eureka-tst-ns"
            K8S_STG_NAMESPACE = "eureka-stage-ns"
            HELM_PATH = "${workspace}/i27-sharedlib/chart"
            DEV_ENV = "dev"
            TST_ENV = "tst"
            STAGE_ENV = "stage"
            PROD_ENV = "prd"
            IMAGE_TAG = "${GIT_COMMIT}"
        }
        tools {
            maven 'maven-3.8.8'
            jdk 'Jdk17'
        }
        stages {

               stage('cleanup of i27-sharedlib before cloning ')
            {
                steps{

                script { 
                    // Change to the workspace directory and remove i27-sharedlib directory 
                    dir("${workspace}") {
                    sh 'rm -rf i27-sharedlib'  
                }
            }
                }
            }

            stage('Git checkout')
            {
                steps{

                script{
                   k8s.gitclone()
                }
                }
            }
            stage('Authenticate to AKS') {
                when {
                    expression { params.k8Login == 'YES' }
                }
                steps {
                    echo "Authenticating with AKS"
                    script {
             
                        k8s.akslogin(env.AZURE_CLIENT_ID,env.AZURE_CLIENT_SECRET,env.AZURE_TENANT_ID,env.AZURE_SUBSCRIPTION_ID,env.RESOURCE_GROUP,env.AKS_CLUSTER_NAME)
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
            stage('Deploy to DEV') {
                when {
                    expression { params.deployToDev == 'YES' }
                }
                steps {
                    script {
                        imageValidation(build)
                        // def docker_image = "${env.DOCKERHUB}/${env.APPLICATION_NAME}:${IMAGE_TAG}"
                        // def jfrog_image = "${env.JFROG_REPO}/${env.APPLICATION_NAME}:${IMAGE_TAG}"
                        k8s.akslogin(env.AZURE_CLIENT_ID,env.AZURE_CLIENT_SECRET,env.AZURE_TENANT_ID,env.AZURE_SUBSCRIPTION_ID,env.RESOURCE_GROUP,env.AKS_CLUSTER_NAME)
                        //k8s.aksdeploy("${env.K8S_DEV_FILE}",docker_image,"${env.K8S_DEV_NAMESPACE}")
                        k8s.k8sHelmChartDeploy("${env.APPLICATION_NAME}", "${env.DEV_ENV}", "${env.HELM_PATH}","${K8S_DEV_NAMESPACE}","${IMAGE_TAG}")
                       //DockerDeploy('dev', '5761', '8761')
                        echo "Deployed to ${env.STAGE_ENV} Successfully"
                    }
                }
            }
            stage('Docker Deploy to TEST') {
                when {
                    expression { params.deployToTest == 'YES' }
                }
                steps {
                    script {
                        //imageValidation(build)
                        // def jfrog_image = "${env.JFROG_REPO}/${env.APPLICATION_NAME}:${IMAGE_TAG}"
                        k8s.akslogin(env.AZURE_CLIENT_ID,env.AZURE_CLIENT_SECRET,env.AZURE_TENANT_ID,env.AZURE_SUBSCRIPTION_ID,env.RESOURCE_GROUP,env.AKS_CLUSTER_NAME)
                        //k8s.aksdeploy("${env.K8S_DEV_FILE}",docker_image,"${env.K8S_DEV_NAMESPACE}")
                        k8s.k8sHelmChartDeploy("${env.APPLICATION_NAME}", "${env.TST_ENV}", "${env.HELM_PATH}","${K8S_TST_NAMESPACE}","${IMAGE_TAG}")
                       //DockerDeploy('dev', '5761', '8761')
                        echo "Deployed to ${env.STAGE_ENV} Successfully"
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
                         //imageValidation(build)
                        // def jfrog_image = "${env.JFROG_REPO}/${env.APPLICATION_NAME}:${IMAGE_TAG}"
                        k8s.akslogin(env.AZURE_CLIENT_ID,env.AZURE_CLIENT_SECRET,env.AZURE_TENANT_ID,env.AZURE_SUBSCRIPTION_ID,env.RESOURCE_GROUP,env.AKS_CLUSTER_NAME)
                        //k8s.aksdeploy("${env.K8S_DEV_FILE}",docker_image,"${env.K8S_DEV_NAMESPACE}")
                        k8s.k8sHelmChartDeploy("${env.APPLICATION_NAME}", "${env.STAGE_ENV}", "${env.HELM_PATH}","${K8S_STG_NAMESPACE}","${IMAGE_TAG}")
                       //DockerDeploy('dev', '5761', '8761')
                        echo "Deployed to ${env.STAGE_ENV} Successfully"
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

// Helper functions to deploy the container into docker server 
def DockerDeploy(envDeploy, hostPort, containerPort) {
    echo "Deploying to Docker $envDeploy"
    withCredentials([usernamePassword(credentialsId: 'docker_dev_server', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
        try {
            sh """
            sshpass -p ${env.PASSWORD} ssh -o StrictHostKeyChecking=no ${env.USERNAME}@${env.docker_dev_server} docker pull ${env.DOCKERHUB}/${env.APPLICATION_NAME}:${IMAGE_TAG}
            sshpass -p ${env.PASSWORD} ssh -o StrictHostKeyChecking=no ${env.USERNAME}@${env.docker_dev_server} docker stop ${env.APPLICATION_NAME}-$envDeploy || true
            sshpass -p ${env.PASSWORD} ssh -o StrictHostKeyChecking=no ${env.USERNAME}@${env.docker_dev_server} docker rm ${env.APPLICATION_NAME}-$envDeploy || true
            sshpass -p ${env.PASSWORD} ssh -o StrictHostKeyChecking=no ${env.USERNAME}@${env.docker_dev_server} docker run -d -p $hostPort:$containerPort --name ${env.APPLICATION_NAME}-$envDeploy ${env.DOCKERHUB}/${env.APPLICATION_NAME}:${IMAGE_TAG}
            """
        } catch (err) {
            echo "Error during Docker deployment: ${err}"
            throw err
        }
    }
}

def imageValidation(build) {
    try {
        sh "docker login -u ${JFROG_CREDS_USR} -p ${JFROG_CREDS_PSW}  i27project.jfrog.io"
        sh "docker pull ${env.JFROG_REPO}/${JFROG_REGISTRY}/${env.APPLICATION_NAME}:${IMAGE_TAG}"
    } catch (Exception e) {
        echo "Image not found. Building and pushing the image."
        build.applicationBuild(env.APPLICATION_NAME)
        dockerBuildPush()
    }
}

// this function will build the image and publish the image to jfrog dokcer degistry 
def dockerBuildPush() {
    sh """
    mkdir -p .cicd
    cp target/i27-${env.APPLICATION_NAME}-${POM_VERSION}.${POM_PACKAGING} .cicd/
    docker build --force-rm --no-cache --pull --rm=true --build-arg JAR_SOURCE=i27-${env.APPLICATION_NAME}-${POM_VERSION}.${POM_PACKAGING} -t ${env.JFROG_REPO}/${JFROG_REGISTRY}/${env.APPLICATION_NAME}:${GIT_COMMIT} .cicd
    docker login -u ${JFROG_CREDS_USR} -p ${JFROG_CREDS_PSW}  i27project.jfrog.io
    docker push ${env.JFROG_REPO}/${JFROG_REGISTRY}/${env.APPLICATION_NAME}:${IMAGE_TAG}
    """
}
