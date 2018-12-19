pipeline {
    agent any
    stages {
        stage('Launch command') {
            steps {
                withCredentials([
                    usernamePassword(credentialsId: 'users-elasticsearch-admin', passwordVariable: 'ES_PASS', usernameVariable: 'ES_USER'),
                    file(credentialsId: 'certs-elasticsearch-ca', variable: 'ES_CA_CERT')
                ]) {

                    sh script: """
                        curl -XGET -u $ES_USER:$ES_PASS 'https://172.23.233.241:9200/_cat/health' --cacert $ES_CA_CERT
                    """
                }
            }
        }
    }
}