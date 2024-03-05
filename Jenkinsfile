#!/usr/bin/env groovy

pipeline {
    agent {
        label 'maven-3-9-5-eclipse-temurin-21'
    }
    stages {
        stage('Build') {
            steps {
                sh './mvnw -ntp -B -U clean package'
                sh './mvnw -ntp -B --projects klab.core.api javadoc:javadoc'
                withCredentials([sshUserPrivateKey(credentialsId: 'jenkins-im-communication', keyFileVariable: 'identity')]) {
                    sh 'rsync --archive --progress --delete --rsh="ssh -i ${identity} -o StrictHostKeyChecking=no" klab.core.api/target/site/apidocs/ ubuntu@192.168.250.200:repos/documents.production.compose/javadocs/'
                }
            }
        }
    }
}
