def mail_body=""

pipeline{
    agent any
    environment{
        RMT_REG = "dockerhub.hi.inet"
        SD_REG = "registry.smartdigits.io" 
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
        stage('Create file'){
            steps{
                writeFile file: 'anchore-images.data', text: "${params.image}"
            }
        }
        stage('Image scan'){
            steps{
                anchore 'anchore-images.data'
				withCredentials([usernamePassword(credentialsId: 'anchore-admin', usernameVariable: 'ANCHORE_CLI_USER', passwordVariable: 'ANCHORE_CLI_PASS')]) {
					sh script: """
						#!/bin/env bash

						set +x

						>scan.data

						DIV='============================================================'

						printf "%s\n\t%s\n%s\n" "\${DIV}" "${params.image}" "\${DIV}" >>scan.data
						anchore-cli image vuln ${params.image} all 2>/dev/null >>scan.data
						printf "\n\n" >>scan.data
					"""
				}
            }
        }
        stage('Image push'){
            steps{
                script{
                    docker.withRegistry('https://${SD_REG}', 'dockerhub-devops-user') {
                        sh script: """
							#!/bin/env bash

							set +x

                            RELEASE=${params.release}
                            
                            docker pull ${params.image}
                            sd_image=\$(echo ${params.image} | sed 's/^${env.RMT_REG}\\(.*\\)/${env.SD_REG}\\1/g')
                            
                            if [ -e \$RELEASE ]
                            then
                                docker tag ${params.image} \${sd_image}
                            else
                                echo "FROM \${sd_image}" | docker build --label RELEASE="\${RELEASE}" -t \${sd_image} -
                            fi
                            
                            docker push \${sd_image}
                        """
                    }
                }
            }
        }
    }
    post{
        always{
            script{
                if( currentBuild.getCurrentResult() == "SUCCESS"){
                    mail_body = "Image ${params.image} successfully scanned and pushed to ${env.SD_REG}"    
                }
                else{
                    mail_body = "Image ${params.image} has not passed vulnerability scan"     
                }

				notify([ type: "slack-default-end", message: "Finished scanning image ${params.image}" ])
                
                emailext attachLog: true, 
                    subject: "Vulnerability scan results for image ${params.image} [${currentBuild.getCurrentResult()}]",
                    to: 'luismiguel.saezmartin.ext@telefonica.com',
                    replyTo: 'smartdigits_devops@telefonica.com',
                    body: "${mail_body}",
					attachmentsPattern: 'scan.data'
            }
        }
    }
}