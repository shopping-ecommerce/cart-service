package iuh.fit.se.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CartItemSummaryResponse {
    String productId;
    String productName;   // sẽ fetch từ product-service, demo thì hardcode
    String productImage;
    Integer quantity;
    BigDecimal unitPrice;
    BigDecimal totalPrice;
    String size;
}
