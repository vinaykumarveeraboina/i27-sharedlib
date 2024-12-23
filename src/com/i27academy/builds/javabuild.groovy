package com.i27academy.builds

class Docker{
    def jenkins
    Docker jenkins {
        jenkins = this.jenkins
}

//Application Build

def applicationBuild() {

     jenkins.sh """#!/bin/bash
    echo "Building the ${env.APPLICATION_NAME} application"
    sh "mvn clean package -DskipTests=true"

    """
}

}
