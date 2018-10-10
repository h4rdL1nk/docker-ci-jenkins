pipeline{
    agent any
    stages{
        stage('Code checkout'){
            steps {
                script {
                    scmVars = checkout scm
                    notify([ type: "slack-default-start" ])
                }
            }
        }
        stage('Add registry images'){
            steps{
                withCredentials([
						usernamePassword(credentialsId: 'dockerhub-devops-user', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD'),
						usernamePassword(credentialsId: 'anchore-admin', usernameVariable: 'ANCHORE_CLI_USER', passwordVariable: 'ANCHORE_CLI_PASS')
				]){
                    sh script: """
                        #!/bin/env bash 
                        
						set +x
                        
						>repositories.data

						DIV='============================================================'
						
                        reg_user=$USERNAME
                        reg_pass=$PASSWORD
                        
                        curl -sL -u \${reg_user}:\${reg_pass} https://${INFRA_REGISTRY_URL}/v2/_catalog | jq -r '.repositories[]' >repositories.data

						cat repositories.data | while read R
						do 
							curl -sL -u \${reg_user}:\${reg_pass} https://${INFRA_REGISTRY_URL}/v2/\${R}/tags/list | jq -r '.tags[]' | \
							while read T
							do 
								IMG="\${R}:\${T}"

								if ! `anchore-cli image get ${INFRA_REGISTRY_URL}/\${IMG} >/dev/null 2>&1`
								then
									printf "\nAdding image to anchore engine [\${IMG}] ...\n" >>${BUILD_TAG}.data
									anchore-cli image add ${INFRA_REGISTRY_URL}/\${IMG} 2>/dev/null >>${BUILD_TAG}.data
									printf "\n\n" >>${BUILD_TAG}.data 
								fi		
							done
						done
                    """
                }
            }
        }
    }
    post{
        always{
            script {
                notify([ type: "slack-default-end", message: "Finished scanning latest images" ])

				def exists = fileExists "${BUILD_TAG}.data"

				if (exists){
					emailext attachLog: true, 
						body: "Registry images added to anchore engine; find attached files for output\nBuild URL: ${BUILD_URL}", 
						subject: "Registry images added to anchore engine ${BUILD_TAG}", 
						to: 'smartdigits_devops@telefonica.com',
						replyTo: 'smartdigits_devops@telefonica.com',
						attachmentsPattern: "${BUILD_TAG}.data"
				}
            }
            deleteDir()
        }
    }
}