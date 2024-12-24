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

    
}