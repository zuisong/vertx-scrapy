pipeline {
    agent any
    stages {
        stage('build') {
            steps {
                sh 'java -version'
                sh 'sh gradlew tasks'
                sh 'sh gradlew build'
            }
        }
    }
}