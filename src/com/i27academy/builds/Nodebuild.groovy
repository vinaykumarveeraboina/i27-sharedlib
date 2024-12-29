package com.i27academy.builds

class Nodebuild {
    def jenkins
    Nodebuild(jenkins) {
        this.jenkins = jenkins
    }

    // Application Build
    def applicationBuild(appName) {

    
   
        jenkins.sh """ 
        
        echo "Building the ${appName} application"
        npm install

        """  

}
}