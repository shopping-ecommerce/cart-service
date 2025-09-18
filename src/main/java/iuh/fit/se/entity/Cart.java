package iuh.fit.se.entity;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RedisHash("cart")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Cart {
    @Id
    String id; // userIdvbb

    @Indexed
    String userId;

    @Builder.Default
    List<CartItem> items = new ArrayList<>();

    BigDecimal subtotal;
    BigDecimal totalDiscount;
    BigDecimal estimatedShipping;
    BigDecimal totalAmount;

    LocalDateTime createdAt;
    LocalDateTime updatedAt;


    // TTL - giỏ hàng sẽ expire sau 7 ngày không hoạt động
    @TimeToLive(unit = TimeUnit.DAYS)
    Long ttl = 7L;

    public void calculateTotals() {
        this.subtotal = items.stream()
                .map(CartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalDiscount = items.stream()
                .map(item -> {
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Estimate shipping (free if > 500k VND)
        this.estimatedShipping = subtotal.compareTo(BigDecimal.valueOf(500000)) >= 0
                ? BigDecimal.ZERO : BigDecimal.valueOf(30000);

        this.totalAmount = subtotal.add(estimatedShipping);

        this.updatedAt = LocalDateTime.now();
    }

    public int getTotalItems() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
    }
}