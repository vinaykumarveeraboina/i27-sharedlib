package com.i27academy.builds

class Javabuild{
    def jenkins
    Javabuild (jenkins) {
      this.jenkins =  jenkins 
}

//Application Build

def applicationBuild() {

     jenkins.sh """ 
      echo "Building the ${env.APPLICATION_NAME} application"
      sh "mvn clean package -DskipTests=true"
   
    """
}

}
