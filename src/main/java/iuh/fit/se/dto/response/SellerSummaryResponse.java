package iuh.fit.se.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SellerSummaryResponse {
    String sellerId;
    String sellerName;
    Integer itemCount;
    BigDecimal subtotal;
    BigDecimal shippingFee;
    Boolean freeShipping;
    BigDecimal amountForFreeShipping;
    List<CartItemSummaryResponse> items;   // 👈 thêm list item
}
