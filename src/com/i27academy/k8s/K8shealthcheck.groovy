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

       def k8sobjectstatus(namespace)
      {
    
                    jenkins.sh """
                            echo " *************************  NODE details and status   ******************** "


                                     kubectl get nodes

                            echo " *************************  k8s NAMESPACEs  ******************** "

                                     kubectl get ns

                            echo " *************************  k8s DEPloyment details in ${namespace}  ******************** "

                                     kubectl get deploy -n ${namespace}

                            echo " ************************* k8s POD Status in ${namespace}  ******************** "

                                     kubectl get pods -n ${namespace}


                        """

      }

      

}