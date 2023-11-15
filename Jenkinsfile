pipeline {
    agent {
        label 'maven-3-9-5-eclipse-temurin-21'
    }
    stages {
        stage('Build') {
            steps {
                sh 'mvn -v'
            }
        }
    }
}
