package com.i27academy.k8s

class K8s {
    def jenkins
    K8s(jenkins) {
        this.jenkins = jenkins
    }

   //we will write alsl the kubernets related files here 

      def akslogin(AZURE_CLIENT_ID,String AZURE_CLIENT_SECRET,String AZURE_TENANT_ID,String AZURE_SUBSCRIPTION_ID,String RESOURCE_GROUP,String AKS_CLUSTER_NAME)
      
     {
                    jenkins.sh """
                    
                            az login --service-principal -u ${AZURE_CLIENT_ID} -p ${AZURE_CLIENT_SECRET} --tenant ${AZURE_TENANT_ID}
                            az account set --subscription ${AZURE_SUBSCRIPTION_ID}
                            az aks get-credentials --resource-group ${RESOURCE_GROUP} --name ${AKS_CLUSTER_NAME} --overwrite-existing
                            kubectl get nodes

                        """

      }




}
