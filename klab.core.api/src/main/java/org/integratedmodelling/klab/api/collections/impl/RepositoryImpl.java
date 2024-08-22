//package org.integratedmodelling.klab.api.collections.impl;
//
//import org.integratedmodelling.klab.api.data.Repository;
//
//import java.net.URL;
//import java.util.ArrayList;
//import java.util.List;
//
//public class RepositoryImpl implements Repository {
//
//    private Status status;
//    private URL repositoryUrl;
//    private String currentBranch;
//    private List<String> branches = new ArrayList<>();
//
//    @Override
//    public Status getStatus() {
//        return status;
//    }
//
//    @Override
//    public URL getRepositoryUrl() {
//        return repositoryUrl;
//    }
//
//    @Override
//    public String getCurrentBranch() {
//        return currentBranch;
//    }
//
//    public void setStatus(Status status) {
//        this.status = status;
//    }
//
//    public void setRepositoryUrl(URL repositoryUrl) {
//        this.repositoryUrl = repositoryUrl;
//    }
//
//    public void setCurrentBranch(String currentBranch) {
//        this.currentBranch = currentBranch;
//    }
//
//    @Override
//    public List<String> getBranches() {
//        return branches;
//    }
//
//    public void setBranches(List<String> branches) {
//        this.branches = branches;
//    }
//}
