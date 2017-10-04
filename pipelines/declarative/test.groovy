pipeline{
    agent{
        label 'worker'
    }
    stages{
        stage("build"){
            steps{
                    sh '''
                        echo Variables from shell:
                        echo reference $git_repo
                    ''' 
            }
        }
    }
}