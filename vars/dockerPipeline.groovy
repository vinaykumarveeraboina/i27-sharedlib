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


// Docker Build and Push
    def dockerBuildPush() {
        jenkins.sh """
        ls -la
        cp ${workspace}/target/i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} ./.cicd
        ls -la ./.cicd
        echo "*********************** Building Docker Image *********************************"
        docker build --force-rm --no-cache --pull --rm=true --build-arg JAR_SOURCE=i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} -t ${env.DOCKERHUB}/${env.APPLICATION_NAME}:${env.GIT_COMMIT} ./.cicd
        docker images
        echo "***************** Docker login ************************"
        docker login -u ${env.DOCKER_CREDS_USR} -p ${env.DOCKER_CREDS_PSW}
        echo "********************* Docker push *************************************"
        docker push ${env.DOCKERHUB}/${env.APPLICATION_NAME}:${env.GIT_COMMIT}
        """
    }

    // Image Validation
    def imagevalidation() {
        println(" **********************  pulling the docker image *******************************")
        try {
            jenkins.sh """
            docker pull ${env.DOCKERHUB}/${env.APPLICATION_NAME}:${env.GIT_COMMIT}
            """
        } catch (Exception e) {
            println(" *******************   OOPS! Image with this tag is not available  ************************* ")
            println("*********************** Building Application  *****************************************")
            build.applicationBuild('${env.APPLICATION_NAME}')
            println("*********************** Image build and push to Hub  *****************************************")
            dockerBuildPush()
        }
    }

    // Deploying to Docker in different environments
    def DockerDeploy(envdeploy, hostport, contport) {
        echo "************************ Deploying to Docker $envdeploy ********************************"

        withCredentials([usernamePassword(credentialsId: 'docker_dev_server', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
            echo "****************** PULLING the container from docker hub ********************"
            jenkins.sh """
            sshpass -p ${env.PASSWORD} ssh -o StrictHostKeyChecking=no ${env.USERNAME}@${env.docker_dev_server} docker pull ${env.DOCKERHUB}/${env.APPLICATION_NAME}:${env.GIT_COMMIT}
            """
            script {
                try {
                    echo "****************** Stopping the container ********************"
                    jenkins.sh """
                    sshpass -p ${env.PASSWORD} ssh -o StrictHostKeyChecking=no ${env.USERNAME}@${env.docker_dev_server} docker stop ${env.APPLICATION_NAME}-$envdeploy
                    """

                    echo "****************** Removing the container ********************"
                    jenkins.sh """
                    sshpass -p ${env.PASSWORD} ssh -o StrictHostKeyChecking=no ${env.USERNAME}@${env.docker_dev_server} docker rm ${env.APPLICATION_NAME}-$envdeploy
                    """
                } catch (err) {
                    echo "Caught the Error: ${err}"
                }
            }

            echo "**************** Running the container *****************"
            jenkins.sh """
            sshpass -p ${env.PASSWORD} ssh -o StrictHostKeyChecking=no ${env.USERNAME}@${env.docker_dev_server} docker run -d -p $hostport:$contport --name ${env.APPLICATION_NAME}-$envdeploy ${env.DOCKERHUB}/${env.APPLICATION_NAME}:${env.GIT_COMMIT}
            """
        }
    }

