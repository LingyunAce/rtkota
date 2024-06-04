package com.ostar.ota.util;


public class InstallProgress {
    private int progress;
    private boolean isDownloaded;
    private boolean isInstalled;
    private boolean isAfterExecuted;

    public InstallProgress() {
        this.progress = 0;
        this.isDownloaded = false;
        this.isInstalled = false;
        this.isAfterExecuted = false;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public boolean isDownloaded() {
        return isDownloaded;
    }

    public void setDownloaded(boolean downloaded) {
        isDownloaded = downloaded;
    }

    public boolean isInstalled() {
        return isInstalled;
    }

    public void setInstalled(boolean installed) {
        isInstalled = installed;
    }

    public boolean isAfterExecuted() {
        return isAfterExecuted;
    }

    public void setAfterExecuted(boolean afterExecuted) {
        isAfterExecuted = afterExecuted;
    }

    @Override
    public String toString() {
        return "InstallProgress{" +
                ", progress=" + progress +
                ", isDownloaded=" + isDownloaded +
                ", isInstalled=" + isInstalled +
                ", isAfterExecuted=" + isAfterExecuted +
                '}';
    }
}
