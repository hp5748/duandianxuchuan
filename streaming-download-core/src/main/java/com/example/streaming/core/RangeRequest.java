package com.example.streaming.core;

/**
 * Range 请求解析
 * 解析 HTTP Range 请求头，如 "bytes=0-1023"
 */
public class RangeRequest {

    private final long start;
    private final long end;
    private final long totalLength;

    private RangeRequest(long start, long end, long totalLength) {
        this.start = start;
        this.end = end;
        this.totalLength = totalLength;
    }

    /**
     * 解析 Range 请求头
     *
     * @param rangeHeader  Range 请求头，如 "bytes=0-1023"
     * @param totalLength  文件总大小
     * @return RangeRequest 对象，如果 header 为空或无效返回 null
     */
    public static RangeRequest parse(String rangeHeader, long totalLength) {
        if (rangeHeader == null || rangeHeader.isEmpty() || totalLength <= 0) {
            return null;
        }

        // 格式: bytes=0-1023 或 bytes=1024-
        if (!rangeHeader.startsWith("bytes=")) {
            return null;
        }

        String byteRange = rangeHeader.substring(6).trim();

        // 仅支持单段 Range，不支持多段 (bytes=0-100,200-300)
        if (byteRange.contains(",")) {
            return null;
        }

        try {
            long start;
            long end;

            int dashIndex = byteRange.indexOf('-');
            if (dashIndex == -1) {
                return null;
            }

            String startStr = byteRange.substring(0, dashIndex);
            String endStr = byteRange.substring(dashIndex + 1);

            if (startStr.isEmpty()) {
                // 格式: bytes=-500 (最后 500 字节)
                long suffixLength = Long.parseLong(endStr);
                start = Math.max(0, totalLength - suffixLength);
                end = totalLength - 1;
            } else {
                start = Long.parseLong(startStr);
                if (endStr.isEmpty()) {
                    // 格式: bytes=1000- (从 1000 到末尾)
                    end = totalLength - 1;
                } else {
                    end = Long.parseLong(endStr);
                }
            }

            // 校验范围有效性
            if (start < 0 || start >= totalLength) {
                return null;
            }

            // 确保 end 不超过文件末尾
            if (end >= totalLength) {
                end = totalLength - 1;
            }

            // 确保 end >= start
            if (end < start) {
                return null;
            }

            return new RangeRequest(start, end, totalLength);

        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取实际读取长度
     */
    public long getContentLength() {
        return end - start + 1;
    }

    /**
     * 生成 Content-Range 响应头
     * 格式: "bytes 0-1023/5000"
     */
    public String toContentRange() {
        return String.format("bytes %d-%d/%d", start, end, totalLength);
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public long getTotalLength() {
        return totalLength;
    }

    /**
     * 是否为部分内容请求
     */
    public boolean isPartial() {
        return start > 0 || end < totalLength - 1;
    }

    @Override
    public String toString() {
        return String.format("RangeRequest{start=%d, end=%d, totalLength=%d}", start, end, totalLength);
    }
}
