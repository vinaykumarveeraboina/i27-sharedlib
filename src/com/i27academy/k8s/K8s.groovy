package com.i27academy.k8s

class K8s {
    def jenkins
    K8s(jenkins) {
        this.jenkins = jenkins
    }

   //we will write all the kubernets related files here 

      def akslogin()
      
     {
                    jenkins.sh """
                    
                            az login --service-principal -u e174eda7-af95-4e7b-a3dc-c7b307f19ac5 -p WVQ8Q~PwIQ_R.Lt8dJSFN5iS2XacHPTwBIVnkaMP --tenant cf10d2ad-2334-4fc8-81da-b44a26739199
                            az account set --subscription b056fb7b-25fb-4735-9bd4-39d1c66b6e5a


                        """

}
}
