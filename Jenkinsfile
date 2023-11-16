#!/usr/bin/env groovy

def remote = [:]
remote.name = "im-communication"
remote.host = "192.168.250.200"
remote.allowAnyHosts = true

pipeline {
    agent {
        label 'maven-3-9-5-eclipse-temurin-21'
    }
    stages {
        stage('Build') {
            steps {
                sh 'mvn -B -U clean package'
                sh 'mvn -B --projects klab.core.api javadoc:javadoc'
                withCredentials([sshUserPrivateKey(credentialsId: 'jenkins-im-communication', keyFileVariable: 'identity', passphraseVariable: '', usernameVariable: 'ubuntu')]) {
                    remote.user = userName
                    remote.identityFile = identity
                    stage("Copy Javadocs") {
                        sshPut remote: remote, from: 'klab.core.api/target/site/apidocs/', into: '/home/ubuntu/repos/documents.production.compose/javadocs'
                    }
                }
            }
        }
    }
}
