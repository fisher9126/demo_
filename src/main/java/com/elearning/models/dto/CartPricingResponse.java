package com.elearning.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Kết quả tính tiền giỏ hàng trả về cho client.
 * originalTotal : tổng tiền ban đầu (chưa giảm)
 * discountAmount: số tiền được giảm (đã áp mức chặn trần)
 * finalTotal    : số tiền cuối cùng phải trả
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartPricingResponse {
    private BigDecimal originalTotal;
    private BigDecimal discountAmount;
    private BigDecimal finalTotal;
    private String appliedVoucherCode;
}
