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
public class CartSummaryResponse {
    Integer totalItems;
    Integer totalSellers;
    BigDecimal subtotal;
    BigDecimal totalShipping;
    BigDecimal totalDiscount;
    BigDecimal finalAmount;
    List<SellerSummaryResponse> sellerSummaries;
    Boolean hasOutOfStockItems;
    Boolean canCheckout;
    String checkoutMessage;
}
