package com.elearning.models.services;

import com.elearning.exceptions.BusinessException;
import com.elearning.models.dto.CartPricingResponse;
import com.elearning.models.entities.Cart;
import com.elearning.models.entities.CartItem;
import com.elearning.models.entities.Course;
import com.elearning.models.entities.DiscountType;
import com.elearning.models.entities.User;
import com.elearning.models.entities.Voucher;
import com.elearning.models.repositories.CartRepository;
import com.elearning.models.repositories.UserRepository;
import com.elearning.models.repositories.VoucherRepository;
import com.elearning.models.services.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CartService cartService;

    private static final Long USER_ID = 1L;

    private Voucher blackFridayVoucher;

    @BeforeEach
    void setUp() {
        // Voucher "giảm sâu": 20% nhưng trần 500.000, yêu cầu >= 2 khóa học.
        blackFridayVoucher = new Voucher();
        blackFridayVoucher.setId(10L);
        blackFridayVoucher.setCode("BF2026");
        blackFridayVoucher.setDiscountType(DiscountType.PERCENTAGE);
        blackFridayVoucher.setDiscountValue(new BigDecimal("20"));
        blackFridayVoucher.setMaxDiscountAmount(new BigDecimal("500000"));
        blackFridayVoucher.setMinCourseCount(2);
        blackFridayVoucher.setActive(true);
        blackFridayVoucher.setStartDate(LocalDateTime.now().minusDays(1));
        blackFridayVoucher.setEndDate(LocalDateTime.now().plusDays(30));

        lenient().when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Cart buildCart(BigDecimal... prices) {
        User user = new User();
        user.setId(USER_ID);

        Cart cart = new Cart();
        cart.setId(100L);
        cart.setUser(user);

        List<CartItem> items = new ArrayList<>();
        long courseId = 1;
        for (BigDecimal price : prices) {
            Course course = new Course();
            course.setId(courseId++);
            course.setPrice(price);

            CartItem item = new CartItem();
            item.setCart(cart);
            item.setCourse(course);
            item.setPriceAtAdded(price);
            items.add(item);
        }
        cart.setItems(items);
        return cart;
    }

    // KỊCH BẢN A: 3 khóa học, tổng 3.000.000. 20% = 600.000 > trần 500.000 => giảm đúng 500.000.
    @Test
    void applyVoucher_capsDiscountAt500k() {
        Cart cart = buildCart(new BigDecimal("1000000"),
                new BigDecimal("1000000"),
                new BigDecimal("1000000"));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(voucherRepository.findByCode("BF2026")).thenReturn(Optional.of(blackFridayVoucher));

        CartPricingResponse result = cartService.applyVoucher(USER_ID, "BF2026");

        assertEquals(0, new BigDecimal("3000000").compareTo(result.getOriginalTotal()));
        assertEquals(0, new BigDecimal("500000").compareTo(result.getDiscountAmount()),
                "20% của 3.000.000 là 600.000 nhưng phải bị chặn ở 500.000");
        assertEquals(0, new BigDecimal("2500000").compareTo(result.getFinalTotal()));
    }

    // KỊCH BẢN B: 2 khóa học, tổng 1.500.000. 20% = 300.000 < trần => giảm đủ 300.000.
    @Test
    void applyVoucher_belowCap_appliesFullPercentage() {
        Cart cart = buildCart(new BigDecimal("800000"), new BigDecimal("700000"));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(voucherRepository.findByCode("BF2026")).thenReturn(Optional.of(blackFridayVoucher));

        CartPricingResponse result = cartService.applyVoucher(USER_ID, "BF2026");

        assertEquals(0, new BigDecimal("1500000").compareTo(result.getOriginalTotal()));
        assertEquals(0, new BigDecimal("300000").compareTo(result.getDiscountAmount()));
        assertEquals(0, new BigDecimal("1200000").compareTo(result.getFinalTotal()));
    }

    // KỊCH BẢN C: 1 khóa học => không đủ điều kiện, phải ném lỗi và không áp mã.
    @Test
    void applyVoucher_singleCourse_throwsBusinessException() {
        Cart cart = buildCart(new BigDecimal("1200000"));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(voucherRepository.findByCode("BF2026")).thenReturn(Optional.of(blackFridayVoucher));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cartService.applyVoucher(USER_ID, "BF2026"));
        assertTrue(ex.getMessage().contains("2 khóa học"));
    }

    // KỊCH BẢN D: mã không tồn tại.
    @Test
    void applyVoucher_unknownCode_throws() {
        Cart cart = buildCart(new BigDecimal("1000000"), new BigDecimal("1000000"));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(voucherRepository.findByCode("XXX")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> cartService.applyVoucher(USER_ID, "XXX"));
    }
}
