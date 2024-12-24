package com.i27academy.builds

class Javabuild {
    def jenkins
    Javabuild(jenkins) {
        this.jenkins = jenkins
    }

    // Application Build
    def applicationBuild(appName) {
        jenkins.sh """ 
        echo "Building the ${appName} application"
        sh "mvn clean package -DskipTests=true"
        """
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
            applicationBuild('${env.APPLICATION_NAME}')
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
}
