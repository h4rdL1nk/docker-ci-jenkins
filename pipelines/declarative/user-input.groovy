def DEPLOY_ENDPOINT
def DEPLOY_PARAMS
def DEPLOY_EXTRA_ENV

pipeline {
    agent any
    parameters {
        string(name: 'DEPLOY_UDO', defaultValue: '', description: 'UDO ticket number')
        string(name: 'DEPLOY_DESC', defaultValue: '', description: 'Optional description')
        string(name: 'DEPLOY_TOKEN_PATH', defaultValue: '/verify/token', description: 'Path for the token operation')
        string(name: 'DEPLOY_CHECK_PATH', defaultValue: '/verify/check', description: 'Path for the check operation')
        string(name: 'DEPLOY_RESULT_PATH', defaultValue: '/verify/result', description: 'Path for the result operation')
        choice(name: 'DEPLOY_ENV', choices: ['pre','sandbox','hack','o2uk-pro','mves-pro'],description: 'Environment to deploy de service')
        choice(name: 'DEPLOY_SVC_NAME', choices: ['msisdn-verify'],description: 'Service name to deploy')
        //gitParameter(name: 'DEPLOY_VERSION', branch: '', branchFilter: '.*', defaultValue: '', description: 'Get deploy version from repository tags', quickFilterEnabled: false, selectedValue: 'NONE', sortMode: 'NONE', tagFilter: '*', type: 'PT_TAG')
        booleanParam(name: 'DEPLOY_SERVICE', defaultValue: true, description: 'Select if the deployment is as a service, instead of dataset operation')
        booleanParam(name: 'DEPLOY_MOCK', defaultValue: true, description: 'Select if the deployment uses mock data for third party calls')
    }
    stages {
        stage('Set environment') {
            steps {
                script {
                    DEPLOY_ENDPOINT = getDeployEndpoint([ env: params.DEPLOY_ENV ])
                    DEPLOY_CREDENTIALS = "dormer-deployment-${params.DEPLOY_ENV}"
                    DEPLOY_PARAMS = ""
                    DEPLOY_EXTRA_ENV = ""

                    if(params.DEPLOY_SERVICE) {
                        DEPLOY_PARAMS = "--service"
                        DEPLOY_EXTRA_ENV = "-e SERVICE_NAME=${params.DEPLOY_SVC_NAME} -e TOKEN_PATH=${params.DEPLOY_TOKEN_PATH} -e CHECK_PATH=${params.DEPLOY_CHECK_PATH} -e RESULT_PATH=${params.DEPLOY_RESULT_PATH}" 
                    }

                    if(['pre','sandbox'].contains(params.DEPLOY_ENV)) {
                        DEPLOY_PARAMS = "${DEPLOY_PARAMS} --mock"
                    }
                }
            }
        }
        stage('Confirm deployment') {
            steps {
                script {
                    def tagList = getGitValue([
                        param: "tagListHttpRepo",
                        httpProto: "https",
                        httpRepo: "pdihub.hi.inet/smartdigits/dormer-msisdn-verify",
                        credentialsId: "pdihub-ro"
                    ])

                    env.DEPLOY_VERSION = input(
                        message: "Deploy to ${DEPLOY_ENDPOINT}",
                        ok: "Run ${params.DEPLOY_ENV} deployment",
                        parameters: [
                            choice(name: 'DEPLOY_VERSION', choices: "${tagList}", description: 'Version to deploy'),
                        ]
                    )

                    print "Version to be deployed: ${env.DEPLOY_VERSION}"
                }
            }
        }
        stage('Prepare deploy command') {
            steps {
                withCredentials([usernamePassword(credentialsId: "${DEPLOY_CREDENTIALS}", passwordVariable: 'API_SECRET', usernameVariable: 'API_KEY')]) {
                    echo """
                        Environment: ${params.DEPLOY_ENV}
                        Endpoint: ${DEPLOY_ENDPOINT}
                        Command: 'docker run --rm -e API_HOST=${DEPLOY_ENDPOINT}/dormer/v1 -e API_KEY -e API_SECRET ${DEPLOY_EXTRA_ENV} dockerhub.hi.inet/smartdigits/${params.DEPLOY_SVC_NAME}:${env.DEPLOY_VERSION} ${DEPLOY_PARAMS}'
                    """
                }
            }
        }
    }
    post{
        always{
            script{
				notify([ type: "slack-default-end", message: "Deployed service ${env.DEPLOY_SVC_NAME}:${env.DEPLOY_VERSION} in *${params.DEPLOY_ENV.toUpperCase()}* environment" ])
            }
        }
    }
}

