package com.i27academy.k8s

class K8s {
    def jenkins
    K8s(jenkins) {
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
                            kubectl get nodes

                        """

      }

    def aksdeploy(filename, docker_image, namespace)
    {
       jenkins.sh """
      echo "Executing AKS deploy"
    
       sed -i "s|DIT|${docker_image}|g" ./.cicd/${filename}
    
       if kubectl apply -f ./.cicd/${filename} -n ${namespace}; then
        echo "Deployment succeeded in namespace ${namespace}"
      else
         echo "OPPS! Namespace ${namespace} is not there!!!"
         echo "Creating the namespace ${namespace}"
         kubectl create namespace ${namespace}
         echo "Deploying the deployment in ${namespace}"
         kubectl apply -f ./.cicd/${filename} -n ${namespace}
      fi
      """
    }

    def k8sHelmChartDeploy(appName, env, helmChartPath, namespace) 
    {
        jenkins.sh """
        echo "********************* Entering into Helm Deployment Method *********************"
        helm version
        echo "Instlling the chart"



        helm install ${appName}-${env}-chart -f ./.cicd/k8s/values_${env}.yaml  ${helmChartPath} -n ${namespace}
        """

     }


   def gitclone()
   {
    jenkins.sh """

    echo " cloning shared library "
    git clone -b main https://github.com/vinaykumarveeraboina/i27-sharedlib.git
    echo " listing the files "

    ls -la 
   echo " listing the files under i27shared repo "

    ls -la i27-sharedlib/chart/

    """
   }

}
