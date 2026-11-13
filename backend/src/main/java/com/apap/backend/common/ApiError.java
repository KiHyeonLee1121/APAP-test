package com.apap.backend.common;

/**
 * 통일 에러 응답 포맷: {"success": false, "error": {"code": ..., "message": ...}}
 */
public record ApiError(
        boolean success,
        ErrorDetail error
) {
    public static ApiError of(String code, String message) {
        return new ApiError(false, new ErrorDetail(code, message));
    }

    public record ErrorDetail(String code, String message) {
    }
}
