package com.example.streaming.core;

/**
 * 路径类型检测器
 * 检测输入路径的类型：HTTP URL、本地文件路径、UNC 路径
 */
public class PathTypeDetector {

    /**
     * 路径类型枚举
     */
    public enum PathType {
        /**
         * HTTP/HTTPS URL
         */
        HTTP_URL,
        /**
         * 本地文件路径（Windows: C:\path, Linux: /home/user/path）
         */
        LOCAL_FILE,
        /**
         * UNC 网络路径（\\server\share\file）
         */
        UNC_PATH
    }

    /**
     * 检测路径类型
     *
     * @param path 输入路径
     * @return 路径类型
     */
    public static PathType detect(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("路径不能为空");
        }

        String trimmedPath = path.trim();

        // 1. 检测 HTTP/HTTPS URL
        if (trimmedPath.toLowerCase().startsWith("http://") ||
            trimmedPath.toLowerCase().startsWith("https://")) {
            return PathType.HTTP_URL;
        }

        // 2. 检测 UNC 路径（\\server\share）
        if (trimmedPath.startsWith("\\\\") || trimmedPath.startsWith("//")) {
            return PathType.UNC_PATH;
        }

        // 3. 检测 Windows 本地路径（C:\, D:\ 等）
        if (trimmedPath.length() >= 2 &&
            Character.isLetter(trimmedPath.charAt(0)) &&
            trimmedPath.charAt(1) == ':') {
            return PathType.LOCAL_FILE;
        }

        // 4. 检测 Unix/Linux 本地路径（以 / 开头）
        if (trimmedPath.startsWith("/")) {
            return PathType.LOCAL_FILE;
        }

        // 默认视为本地文件路径（相对路径等）
        return PathType.LOCAL_FILE;
    }

    /**
     * 判断是否为远程路径（HTTP URL）
     *
     * @param path 输入路径
     * @return 是否为远程路径
     */
    public static boolean isRemote(String path) {
        return detect(path) == PathType.HTTP_URL;
    }

    /**
     * 判断是否为本地路径（本地文件或 UNC）
     *
     * @param path 输入路径
     * @return 是否为本地路径
     */
    public static boolean isLocal(String path) {
        PathType type = detect(path);
        return type == PathType.LOCAL_FILE || type == PathType.UNC_PATH;
    }
}
