package com.elearning.controllers;

import com.elearning.advice.ApiResponse;
import com.elearning.models.dto.ApplyVoucherRequest;
import com.elearning.models.dto.CartPricingResponse;
import com.elearning.models.services.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * Tính tiền giỏ hàng sau khi áp mã giảm giá.
     * Trả về JSON gồm: originalTotal, discountAmount, finalTotal.
     * userId được xác định từ JWT (Authentication), không nhận từ client.
     */
    @PostMapping("/apply-voucher")
    public ResponseEntity<ApiResponse<CartPricingResponse>> applyVoucher(
            @RequestBody ApplyVoucherRequest request,
            Authentication authentication) {

        String email = authentication.getName();
        CartPricingResponse data = cartService.applyVoucherByEmail(email, request.getVoucherCode());
        return ResponseEntity.ok(ApiResponse.success(data, "Áp dụng mã giảm giá thành công"));
    }
}
