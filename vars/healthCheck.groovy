import com.i27academy.k8s.K8shealthcheck

def call(Map pipelineParams) {
    K8shealthcheck healthcheck = new K8shealthcheck(this)

    pipeline {
        agent {
            label 'k8s-slave'
        }
        parameters {
            choice(name: 'NAMESPACE', choices: 'DEV\nTEST\nSTAGE', description: "This will Select NAMESPACE details on Kubernetes")
            choice(name: 'DISPLAY_EVERYTHING', choices: 'NO\nYES', description: "This will display NS, DEPLOY, POD, SVC details on Kubernetes")
            choice(name: 'K8S_POD_STATUS', choices: 'NO\nYES', description: "This will display POD_STATUS on Kubernetes")
        }
        options {
            buildDiscarder(logRotator(daysToKeepStr: '7', numToKeepStr: '5'))
        }
        environment {
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
            stage('Cleanup of i27-sharedlib before cloning') {
                steps {
                    script {
                        dir("${workspace}") {
                            sh 'rm -rf i27-sharedlib'
                        }
                    }
                }
            }

            stage('DEV DETAILS') {
                when {
                    allOf {
                        expression { params.NAMESPACE == 'DEV' }
                        anyOf {
                            expression { params.DISPLAY_EVERYTHING == 'YES' }
                            expression { params.K8S_POD_STATUS == 'YES' }
                        }
                    }
                }
                steps {
                    script {
                        def devDetails = ""
                        healthcheck.akslogin(env.AZURE_CLIENT_ID, env.AZURE_CLIENT_SECRET, env.AZURE_TENANT_ID, env.AZURE_SUBSCRIPTION_ID, env.RESOURCE_GROUP, env.AKS_CLUSTER_NAME)
                        healthcheck.k8snodestatus()
                        if (params.DISPLAY_EVERYTHING == 'YES') {
                            devDetails += healthcheck.k8spsdr(env.K8S_DEV_NAMESPACE)
                        }
                        if (params.K8S_POD_STATUS == 'YES') {
                            devDetails += healthcheck.getPodStatus(env.K8S_DEV_NAMESPACE)
                        }
                        writeFile(file: 'dev_details.txt', text: devDetails)
                    }
                }
            }

            stage('TEST DETAILS') {
                when {
                    allOf {
                        expression { params.NAMESPACE == 'TEST' }
                        anyOf {
                            expression { params.DISPLAY_EVERYTHING == 'YES' }
                            expression { params.K8S_POD_STATUS == 'YES' }
                        }
                    }
                }
                steps {
                    script {
                        def testDetails = ""
                        healthcheck.akslogin(env.AZURE_CLIENT_ID, env.AZURE_CLIENT_SECRET, env.AZURE_TENANT_ID, env.AZURE_SUBSCRIPTION_ID, env.RESOURCE_GROUP, env.AKS_CLUSTER_NAME)
                        healthcheck.k8snodestatus()
                        if (params.DISPLAY_EVERYTHING == 'YES') {
                            testDetails += healthcheck.k8spsdr(env.K8S_TST_NAMESPACE)
                        }
                        if (params.K8S_POD_STATUS == 'YES') {
                            testDetails += healthcheck.getPodStatus(env.K8S_TST_NAMESPACE)
                        }
                        writeFile(file: 'test_details.txt', text: testDetails)
                    }
                }
            }

            stage('STAGE DETAILS') {
                when {
                    allOf {
                        expression { params.NAMESPACE == 'STAGE' }
                        anyOf {
                            expression { params.DISPLAY_EVERYTHING == 'YES' }
                            expression { params.K8S_POD_STATUS == 'YES' }
                        }
                    }
                }
                steps {
                    script {
                        def stageDetails = ""
                        healthcheck.akslogin(env.AZURE_CLIENT_ID, env.AZURE_CLIENT_SECRET, env.AZURE_TENANT_ID, env.AZURE_SUBSCRIPTION_ID, env.RESOURCE_GROUP, env.AKS_CLUSTER_NAME)
                        healthcheck.k8snodestatus()
                        if (params.DISPLAY_EVERYTHING == 'YES') {
                            stageDetails += healthcheck.k8spsdr(env.K8S_STG_NAMESPACE)
                        }
                        if (params.K8S_POD_STATUS == 'YES') {
                            stageDetails += healthcheck.getPodStatus(env.K8S_STG_NAMESPACE)
                        }
                        writeFile(file: 'stage_details.txt', text: stageDetails)
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
