package com.example.streaming.core;

/**
 * Range 响应封装
 * 封装 HTTP Range 响应信息
 */
public class RangeResponse {

    private final boolean partial;
    private final int statusCode;
    private final long contentLength;
    private final String contentRange;
    private final long totalLength;

    private RangeResponse(boolean partial, int statusCode, long contentLength,
                          String contentRange, long totalLength) {
        this.partial = partial;
        this.statusCode = statusCode;
        this.contentLength = contentLength;
        this.contentRange = contentRange;
        this.totalLength = totalLength;
    }

    /**
     * 创建完整下载响应
     */
    public static RangeResponse fullResponse(long totalLength) {
        return new RangeResponse(false, 200, totalLength, null, totalLength);
    }

    /**
     * 创建部分内容响应
     */
    public static RangeResponse partialResponse(RangeRequest rangeRequest) {
        return new RangeResponse(
            true,
            206,
            rangeRequest.getContentLength(),
            rangeRequest.toContentRange(),
            rangeRequest.getTotalLength()
        );
    }

    public boolean isPartial() {
        return partial;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getContentLength() {
        return contentLength;
    }

    public String getContentRange() {
        return contentRange;
    }

    public long getTotalLength() {
        return totalLength;
    }

    @Override
    public String toString() {
        return String.format("RangeResponse{statusCode=%d, contentLength=%d, contentRange='%s'}",
            statusCode, contentLength, contentRange);
    }
}
