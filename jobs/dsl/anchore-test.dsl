pipelineJob("test") {
	description()
	keepDependencies(false)
	definition {
		cpsScm {
"""pipeline {
    agent any
    stages {
        stage('Create anchore images file'){
            steps{
                ansiColor('xterm') {
                    sh script: \"\"\"
                        echo 'debian:wheezy' >> anchore-images.txt
                    \"\"\"
                }
            }
        }
        stage('Analize images'){
            steps{
                ansiColor('xterm') {
                    anchore engineCredentialsId: 'anchore-users-admin', name: 'anchore-images.txt'
                }
            }
        }
    }
}"""		}
	}
	disabled(false)
}