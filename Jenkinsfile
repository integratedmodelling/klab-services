#!/usr/bin/env groovy

pipeline {
    agent {
        label 'maven-3-9-5-eclipse-temurin-21'
    }
    environment {
        MAVEN_OPTS="-Xmx1g"
        MINIO_HOST = "http://192.168.250.150:9000"
        MINIO_CREDENTIALS = "jenkins-ci-minio"
    }
    stages {
        stage('Build') {
            steps {
                sh './mvnw clean source:jar package'
            }
        }
        stage('Deploy artifacts') {
            when {
                anyOf { branch 'develop'; branch 'master' }
            }
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'jenkins-im-communication', keyFileVariable: 'identity')]) {
                    sh './mvnw --projects klab.core.api javadoc:javadoc'
                    sh 'rsync --archive --progress --delete --rsh="ssh -i ${identity} -o StrictHostKeyChecking=no" klab.core.api/target/reports/apidocs/ ubuntu@192.168.250.200:repos/documents.production.compose/javadocs/'
                }
                withCredentials([usernamePassword(credentialsId: "${env.MINIO_CREDENTIALS}", passwordVariable: 'SECRETKEY', usernameVariable: 'ACCESSKEY')]) {
                    sh 'mc alias set minio $MINIO_HOST $ACCESSKEY $SECRETKEY'
                    sh """
                       mc rm --recursive --force minio/klab/p2/org.integratedmodelling.klab.api/ || echo "klab/p2/org.integratedmodelling.klab.api/ does not exists"
                       mc cp --recursive ./p2/org.integratedmodelling.klab.api/target/repository/ minio/klab/p2/org.integratedmodelling.klab.api/
                       mc rm --recursive --force minio/klab/p2/org.integratedmodelling.klab.modeler/ || echo "klab/p2/org.integratedmodelling.klab.modeler/ does not exists"
                       mc cp --recursive ./p2/org.integratedmodelling.klab.modeler/target/repository/ minio/klab/p2/org.integratedmodelling.klab.modeler/
                       mc rm --recursive --force minio/klab/products/klab/ || echo "klab/products/klab/ does not exists"
                       mc cp --recursive ./klab.distribution/target/distribution/ minio/klab/products/klab/
                       """
                }
            }
        }
    }
}
