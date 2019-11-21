pipeline {
  agent {
    docker {
      image 'gradle'
    }

  }
  stages {
    stage('检出') {
      steps {
        checkout([$class: 'GitSCM', branches: [[name: env.GIT_BUILD_REF]],
                                                    userRemoteConfigs: [[url: env.GIT_REPO_URL, credentialsId: env.CREDENTIALS_ID]]])
      }
    }
    stage('构建') {
      steps {
        echo '构建中...'
        sh """
                                                                           echo $PWD
                                                                        """
        sh '''
                    ls -lR
                '''
        sh 'java -version'
        sh 'gradle tasks'
        sh 'gradle build'
        echo ' 构建完成 '
        archiveArtifacts(artifacts: 'build/**/*.jar', fingerprint: true)
      }
    }
  }
}