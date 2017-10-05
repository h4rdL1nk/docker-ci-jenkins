def imgTag
def imgAwsTag
def codeCo
def awsEnv
def gitPushBranch

pipeline {
    agent {
        label 'workeraws'
    }
    parameters {
        string(name: 'DEPARTMENT', defaultValue: 'dummy')
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1')
        string(name: 'APP_NAME', defaultValue: 'dummy')
        booleanParam(name: 'DEPLOY', defaultValue: false)
    }
    options {
        timestamps()
        disableConcurrentBuilds()
    }
    stages {
        stage('Checkout code'){
            steps{
                script{

                    if ( GIT_PUSH ) {
                        gitPushBranch = GIT_BRANCH_0_new_name 
                    } else {
                        def refValues = GIT_REF.split('/')
                        gitPushBranch = refValues[2]
                    }

                    codeCo = checkout scm:[
                                $class: 'GitSCM',
                                poll: true,
                                extensions: [[
                                    $class: 'RelativeTargetDirectory',
                                    relativeTargetDir: 'code'
                                ]],
                                branches: [[
                                    name: '*/${gitPushBranch}'
                                ]],
                                userRemoteConfigs: [[
                                    credentialsId: '1bab7e77-96a9-4fba-9b6d-d0d49b93345c',
                                    url: 'git@bitbucket.org:${GIT_REPOSITORY}'
                                ]]
                            ]
                }
            }
        }
        stage('Docker image build') {
            steps {
                sh 'docker build -t jenkins-${JOB_NAME}-${BUILD_NUMBER}-img ./code'
            }
        }
        stage('Docker image tests') {
            steps{
                sh 'cd code && dgoss run jenkins-${JOB_NAME}-${BUILD_NUMBER}-img'
            }
        }
        stage('Application acceptance tests') {
            steps{
                sh script: """
                    docker run -d -e ENV=dev --name jenkins-${JOB_NAME}-${BUILD_NUMBER}-run jenkins-${JOB_NAME}-${BUILD_NUMBER}-img
                    docker exec -i jenkins-${JOB_NAME}-${BUILD_NUMBER}-run composer require 'codeception/codeception:*'
                    docker exec -i jenkins-${JOB_NAME}-${BUILD_NUMBER}-run php vendor/bin/codecept run --no-colors --json
                    """
            }
        }
        stage('Push image to AWS') {
            steps{
                withAWS(region:"${AWS_REGION}",credentials:"aws-${DEPARTMENT}-admin"){
                    script {
                        def login_cmd = sh script: "aws ecr get-login", returnStdout: true
                        def login_token = login_cmd.split(' ')[5].trim()
                        def login_endpt = login_cmd.split(' ')[8].trim()

                        imgTag = codeCo.GIT_COMMIT
                        imgAwsTag = "${login_endpt.split('//')[1]}/${DEPARTMENT}/${APP_NAME}:${imgTag}"

                        echo "Tag AWS: ${imgAwsTag}"

                        sh script: """
                            docker login -u AWS -p ${login_token} ${login_endpt}
                            docker tag jenkins-${JOB_NAME}-${BUILD_NUMBER}-img ${imgAwsTag}
                            docker push ${imgAwsTag}
                            """, returnStdout: true
                    }
                }
            }
        }
        stage('Push image to local registry') {
            steps{
                withDockerRegistry(url:'https://registry.madisonmk.com',credentialsId:"local-docker-registry"){
                    script{
                        imgTag = codeCo.GIT_COMMIT
                        imgLocalTag = "registry.madisonmk.com/${DEPARTMENT}/${APP_NAME}:${imgTag}"
                        sh script: """
                            docker tag jenkins-${JOB_NAME}-${BUILD_NUMBER}-img ${imgLocalTag}
                            docker push ${imgLocalTag}
                            """, returnStdout: true
                    }
                }
            }
        }
        stage('Deploy application'){
            when{
                expression {
                    return DEPLOY
                }
            }
            steps{
                withAWS(region:"${AWS_REGION}",credentials:"aws-${DEPARTMENT}-admin"){
                    script{
                        awsEnv = "pro"

                        echo "Desplegando imagen: ${imgAwsTag}"

                        def AwsCluster = sh script: """
                            aws ecs list-clusters | jq -r '.clusterArns[]|select(test("^.*CL.*-${awsEnv}\$"))'
                            """, returnStdout: true
                        def AwsService = sh script: """
                            aws ecs list-services --cluster ${AwsCluster.trim()} | jq -r '.serviceArns[]|select(test("^.*:service/SVC-${APP_NAME}(-${awsEnv})?.*\$"))'
                            """, returnStdout: true
                        def AwsTaskDef = sh script: """
                            aws ecs describe-services --cluster ${AwsCluster.trim()} --services ${AwsService.trim()} | jq -r '.services[].deployments[].taskDefinition'
                            """, returnStdout: true
                        def AwsTaskDefJson = sh script: """
                            aws ecs describe-task-definition --task-definition ${AwsTaskDef.trim()} | jq -rc '.taskDefinition|.containerDefinitions[].image="'${imgAwsTag}'"|if .volumes != null then .volumes=.volumes else .volumes=[] end|if .networkMode != null then .networkMode=.networkMode else .networkMode="bridge" end|if .placementConstraints != null then .placementConstraints=.placementConstraints else .placementConstraints=[] end|{family:.family,containerDefinitions:.containerDefinitions,volumes:.volumes,placementConstraints:.placementConstraints,networkMode:.networkMode}'
                            """, returnStdout: true
                        def AwsTaskDefArn = sh script: """
                            aws ecs register-task-definition --cli-input-json '${AwsTaskDefJson.trim()}' | jq -r '.taskDefinition.taskDefinitionArn'
                            """, returnStdout: true
                        def AwsSvcUpdatedTask = sh script: """
                            aws ecs update-service --cluster ${AwsCluster.trim()} --service ${AwsService.trim()} --task-definition ${AwsTaskDefArn.trim()} | jq -r '.service.taskDefinition'
                            """, returnStdout: true

                        echo "Service updated with ${AwsSvcUpdatedTask.trim()}"

                        echo "Finished build ${JOB_NAME}:${BUILD_NUMBER} at node ${NODE_NAME}"
                    }
                }
            }
        }
    }
    post {
        always {
            emailext(
                from: "jenkins-ci@app.madisonmk.com",
                to: "luismiguel.saez@madisonmk.com",
                mimeType: 'text/html',
                subject: "[${currentBuild.currentResult}] ${BUILD_DISPLAY_NAME} ${JOB_NAME}",
                body: "<br>Finalizado ${JOB_NAME} ${BUILD_NUMBER}<br>Nodo:${NODE_NAME}",
                attachLog: true
            )

            deleteDir()

            sh script: """
                if `docker inspect jenkins-${JOB_NAME}-${BUILD_NUMBER}-run >/dev/null 2>&1`
                then
                    docker rm --force jenkins-${JOB_NAME}-${BUILD_NUMBER}-run
                fi
                if `docker inspect jenkins-${JOB_NAME}-${BUILD_NUMBER}-img >/dev/null 2>&1`
                then
                    docker rmi jenkins-${JOB_NAME}-${BUILD_NUMBER}-img
                fi
                if `docker inspect ${imgAwsTag} >/dev/null 2>&1`
                then
                    docker rmi ${imgAwsTag}
                fi
                """, returnStdout: false
        }
    }
}
