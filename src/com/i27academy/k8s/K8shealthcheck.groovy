package com.i27academy.k8s

class K8shealthcheck {
    def jenkins
    K8shealthcheck(jenkins) {
        this.jenkins = jenkins
    }

   //we will write alsl the kubernets related files here 

      def akslogin(AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TENANT_ID, AZURE_SUBSCRIPTION_ID, RESOURCE_GROUP, AKS_CLUSTER_NAME)
      {
    
                    jenkins.sh """
                            echo " ************************* logged in into AKS cluster   ******************** "
                            az login --service-principal -u ${AZURE_CLIENT_ID} -p ${AZURE_CLIENT_SECRET} --tenant ${AZURE_TENANT_ID}
                            az account set --subscription ${AZURE_SUBSCRIPTION_ID}
                            az aks get-credentials --resource-group ${RESOURCE_GROUP} --name ${AKS_CLUSTER_NAME} --overwrite-existing
                        """

      }

       def k8snodestatus()
      {
    
                    jenkins.sh """
                            echo " *************************  NODE details and status   ******************** "


                                     kubectl get nodes

                                    
                        """

      }

      def k8spsdr(namespace)
      { 
        jenkins.sh """
       echo "Fetching Kubernetes objects' status in namespace ${namespace}"
        kubectl get all -n ${namespace} 
        
        """ 
        
     } 
        // Method to get pod status 
        def getPodStatus(namespace) 
        { 
            jenkins.sh """ 
        echo "Fetching pod status in namespace ${namespace}" 
        kubectl get pods -n ${namespace}
         """

         }

      

}