pipeline{
    agent any
	environment{
		REPO_FILTER = "^smartdigits/"
	}
    stages{
        stage('Code checkout'){
            steps {
                script {
                    scmVars = checkout scm
                    notify([ type: "slack-default-start" ])
                }
            }
        }
        stage('Get registry repositories list'){
            steps{
                withCredentials([
						usernamePassword(credentialsId: 'dockerhub-devops-user', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD'),
						usernamePassword(credentialsId: 'anchore-admin', usernameVariable: 'ANCHORE_CLI_USER', passwordVariable: 'ANCHORE_CLI_PASS')
				]){
                    sh script: """
                        #!/bin/env bash 
                        
						set -x
                        
						>repositories.data
						>images.data
						>scan.data

						DIV='============================================================'
						
                        reg_user=$USERNAME
                        reg_pass=$PASSWORD
                        
                        curl -sL -u \${reg_user}:\${reg_pass} https://${INFRA_REGISTRY_URL}/v2/_catalog | jq -r --arg regex ${env.REPO_FILTER} '.repositories[]|select(test(\$regex))' >repositories.data

						cat repositories.data | while read R
						do 
							curl -sL -u \${reg_user}:\${reg_pass} https://${INFRA_REGISTRY_URL}/v2/\${R}/tags/list | jq -r '.tags[]' | \
							while read T
							do 
								IMG="\${R}:\${T}"

								printf "%s\n\t%s\n%s\n" "\${DIV}" "\${IMG}" "\${DIV}" >>${BUILD_TAG}.data
								if ! `anchore-cli image get ${INFRA_REGISTRY_URL}/\${IMG} | egrep "^Analysis Status: analyzed" 2>&1 >/dev/null`
								then
									printf "Image not yet analyzed" >>${BUILD_TAG}.data
								else
									anchore-cli image vuln ${INFRA_REGISTRY_URL}/\${IMG} all 2>/dev/null >>${BUILD_TAG}.data
									echo ${INFRA_REGISTRY_URL}/\${IMG} >>images.data	
								fi
								printf "\n\n" >>${BUILD_TAG}.data	
							done
						done
                    """
                }
                //httpRequest authentication: 'dockerhub-devops-user', validResponseCodes: '200', contentType: 'APPLICATION_JSON', outputFile: 'catalog.json', quiet: true, responseHandle: 'NONE', url: 'https://registry.smartdigits.io/v2/_catalog'
                //sh "cat catalog.json | jq -r '.repositories'"
                //httpRequest authentication: 'dockerhub-devops-user', validResponseCodes: '200', contentType: 'APPLICATION_JSON', outputFile: 'tags.json', quiet: true, responseHandle: 'NONE', url: 'https://registry.smartdigits.io/v2/smartdigits/o2uk-xmno/tags/list'
                //sh "cat tags.json | jq -r '.tags|last'"
            }
        }
		stage('Images vulnerability check'){
            steps{
                anchore bailOnFail: false, name:'images.data', policyBundleId: "${ANCHORE_DEFAULT_POLICY}"
            }
        }
    }
    post{
        always{
            script {
                notify([ type: "slack-default-end", message: "Finished scanning registry images" ])

				def exists = fileExists "${BUILD_TAG}.data"

				if (exists){
					emailext attachLog: true, 
						body: "Registry images vulnerability scan results; find attached files for output\nBuild URL: ${BUILD_URL}", 
						subject: "Registry images vulnerability scan results ${BUILD_TAG}", 
						to: 'smartdigits_devops@telefonica.com',
						replyTo: 'smartdigits_devops@telefonica.com',
						attachmentsPattern: "${BUILD_TAG}.data"
				}
            }
            deleteDir()
        }
    }
}