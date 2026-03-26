package com.example.streaming.core.exception;

/**
 * Range 请求无法满足异常
 * HTTP 416
 */
public class RangeNotSatisfiableException extends DownloadException {

    private final long totalLength;

    public RangeNotSatisfiableException(String message, long totalLength) {
        super(message, 416);
        this.totalLength = totalLength;
    }

    public RangeNotSatisfiableException(long rangeStart, long totalLength) {
        super(String.format("Range 起始位置 %d 超出文件大小 %d", rangeStart, totalLength), 416);
        this.totalLength = totalLength;
    }

    public long getTotalLength() {
        return totalLength;
    }
}
