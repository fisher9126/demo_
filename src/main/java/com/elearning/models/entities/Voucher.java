package com.elearning.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    private DiscountType discountType;

    // Với PERCENTAGE: 20 nghĩa là 20%. Với FIXED: số tiền cố định (VNĐ).
    private BigDecimal discountValue;

    // Mức chặn trần số tiền được giảm (VNĐ). null = không giới hạn.
    private BigDecimal maxDiscountAmount;

    // Số khóa học tối thiểu trong giỏ để được áp mã.
    private Integer minCourseCount;

    private boolean active;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
