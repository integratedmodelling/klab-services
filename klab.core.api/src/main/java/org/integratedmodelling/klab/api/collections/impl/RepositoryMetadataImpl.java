package org.integratedmodelling.klab.api.collections.impl;

import org.integratedmodelling.klab.api.data.RepositoryMetadata;

import java.net.URL;

public class RepositoryMetadataImpl implements RepositoryMetadata {
    private Status status = Status.UNTRACKED;
    private URL repositoryUrl;
    private String currentBranch;

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public URL getRepositoryUrl() {
        return repositoryUrl;
    }

    @Override
    public String getCurrentBranch() {
        return currentBranch;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setRepositoryUrl(URL repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public void setCurrentBranch(String currentBranch) {
        this.currentBranch = currentBranch;
    }
}
