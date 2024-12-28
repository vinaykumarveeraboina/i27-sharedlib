
import com.i27academy.k8s.K8shealthcheck

def call(Map pipelineParams) {
    K8shealthcheck healthcheck = new K8shealthcheck(this)
  
    

    pipeline {
        agent {
            label 'k8s-slave'
        }
        parameters {

            
            choice(name: 'NAMESPACE', choices: 'DEV\nTEST\nSTAGE', description: "This will Select NAMESPACE details on Kubernetes")
            choice(name: 'DISPLAY_EVERYTHING', choices: 'NO\nYES', description: "This will displays NS,DEPLOY,POD,SVC details Kubernetes")
            choice(name: 'K8S_POD_STATUS', choices: 'NO\nYES', description: "This will display POD_STATUS  on Kubernetes")
        }
        options {
            buildDiscarder(logRotator(daysToKeepStr: '7', numToKeepStr: '5'))
        }
        environment {
            // DOCKERHUB = "docker.io/vinaykumarveeraboina"
            JFROG_REPO = 'i27project.jfrog.io'
            JFROG_REGISTRY = 'boutique-docker'
            APPLICATION_NAME = "${pipelineParams.appName}"
            AZURE_CLIENT_ID = credentials('azure-client-id')   
            AZURE_CLIENT_SECRET = credentials('azure-client-secret') 
            AZURE_TENANT_ID = credentials('azure-tenant-id')   
            AZURE_SUBSCRIPTION_ID = credentials('azure-subscription-id')
            RESOURCE_GROUP = 'project'
            AKS_CLUSTER_NAME = 'i27project'
            K8S_DEV_NAMESPACE = "${env.APPLICATION_NAME}-dev-ns"
            K8S_TST_NAMESPACE = "${env.APPLICATION_NAME}-tst-ns"
            K8S_STG_NAMESPACE = "${env.APPLICATION_NAME}-stage-ns"
            DEV_ENV = "dev"
            TST_ENV = "tst"
            STAGE_ENV = "stage"
            PROD_ENV = "prd"
            IMAGE_TAG = "${GIT_COMMIT}"
        }

        stages {

            stage('DEV DETAILS') {
                when {
                    allOf { // All of these conditions must be true expression 

                    expression { params.NAMESPACE == 'DEV' }

                    anyOf {

                    expression { params.DISPLAY_EVERYTHING == 'YES' }
                    
                    expression { params.K8S_POD_STATUS == 'YES' }


                }
                    }
                 }
                steps {
                    script {
                        
                         healthcheck.akslogin(env.AZURE_CLIENT_ID,env.AZURE_CLIENT_SECRET,env.AZURE_TENANT_ID,env.AZURE_SUBSCRIPTION_ID,env.RESOURCE_GROUP,env.AKS_CLUSTER_NAME)

                         healthcheck.k8snodestatus()

                        if (params.DISPLAY_EVERYTHING == 'YES') 
                        {
                             healthcheck.k8spsdr(env.K8S_DEV_NAMESPACE) 
                        } 
                        if (params.K8S_POD_STATUS == 'YES') 
                        { 
                            healthcheck.getPodStatus(env.K8S_DEV_NAMESPACE)

                        }
                       
                   
                   
                    }
                }
            }
             stage('TEST DETAILS') {
                when {
                    allOf { // All of these conditions must be true expression 

                    expression { params.NAMESPACE == 'TEST' }

                    anyOf {

                    expression { params.DISPLAY_EVERYTHING == 'YES' }
                    
                    expression { params.K8S_POD_STATUS == 'YES' }


                }
                    }
                 }
                steps {
                    script {
                        
                        healthcheck.akslogin(env.AZURE_CLIENT_ID,env.AZURE_CLIENT_SECRET,env.AZURE_TENANT_ID,env.AZURE_SUBSCRIPTION_ID,env.RESOURCE_GROUP,env.AKS_CLUSTER_NAME)

                         healthcheck.k8snodestatus()

                        if (params.DISPLAY_EVERYTHING == 'YES') 
                        {
                             healthcheck.k8spsdr(env.K8S_TST_NAMESPACE) 
                             } 
                        if (params.K8S_POD_STATUS == 'YES') 
                        { 
                            healthcheck.getPodStatus(env.K8S_TST_NAMESPACE)

                        }
                       
                   
                   
                    }
                }
            }
            stage('STAGE DETAILS') {
                when {
                    allOf { // All of these conditions must be true expression 

                    expression { params.NAMESPACE == 'STGAE' }

                    anyOf {

                    expression { params.DISPLAY_EVERYTHING == 'YES' }

                    expression { params.K8S_POD_STATUS == 'YES' }


                }
                    }
                 }
                steps {
                    script {
                        
                        healthcheck.akslogin(env.AZURE_CLIENT_ID,env.AZURE_CLIENT_SECRET,env.AZURE_TENANT_ID,env.AZURE_SUBSCRIPTION_ID,env.RESOURCE_GROUP,env.AKS_CLUSTER_NAME)

                         healthcheck.k8snodestatus()

                        if (params.DISPLAY_EVERYTHING == 'YES') 
                        {
                             healthcheck.k8spsdr(env.K8S_STG_NAMESPACE) 
                             } 
                        if (params.K8S_POD_STATUS == 'YES') 
                        { 
                            healthcheck.getPodStatus(env.K8S_STG_NAMESPACE)

                        }
                       
                   
                   
                    }
                }
            }
            stage('Clean Workspace') {
                steps {
                    cleanWs()
                }
            }
        }
    }
}




