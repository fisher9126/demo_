package com.elearning.models.entities;

/**
 * Loại giảm giá của voucher.
 * PERCENTAGE: giảm theo phần trăm tổng đơn (có thể kèm mức chặn trần).
 * FIXED: giảm một số tiền cố định.
 */
public enum DiscountType {
    PERCENTAGE,
    FIXED
}
