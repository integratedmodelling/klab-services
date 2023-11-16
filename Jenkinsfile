pipeline {
    agent {
        label 'maven-3-9-5-eclipse-temurin-21'
    }
    stages {
        stage('Build') {
            steps {
                sh 'mvn -B -U clean package'
                sh 'mvn -B --projects klab.core.api javadoc:javadoc'
            }
        }
    }
}
