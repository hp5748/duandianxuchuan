package com.example.streaming.core;

/**
 * 文件信息封装
 * 包含目标文件的元信息
 */
public class FileInfo {

    private long contentLength;
    private String contentType;
    private boolean acceptRanges;
    private String fileName;

    public FileInfo() {
    }

    public FileInfo(long contentLength, String contentType, boolean acceptRanges, String fileName) {
        this.contentLength = contentLength;
        this.contentType = contentType;
        this.acceptRanges = acceptRanges;
        this.fileName = fileName;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public boolean isAcceptRanges() {
        return acceptRanges;
    }

    public void setAcceptRanges(boolean acceptRanges) {
        this.acceptRanges = acceptRanges;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public String toString() {
        return String.format("FileInfo{contentLength=%d, contentType='%s', acceptRanges=%s, fileName='%s'}",
            contentLength, contentType, acceptRanges, fileName);
    }
}
