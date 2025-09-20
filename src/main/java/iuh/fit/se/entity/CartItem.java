package iuh.fit.se.entity;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CartItem {
    String productId;
    String sellerId;
    String sellerName;
    String size;
    String productImage;
    String productName;
    BigDecimal unitPrice;
    Integer quantity;
    BigDecimal totalPrice;


    public void calculateTotalPrice() {
        if (unitPrice != null && quantity != null) {
            this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    // Unique key for cart item (same product with different size/color = different items)
    public String getUniqueKey() {
        return (sellerId != null ? sellerId : "") + "-" +
                productId + "-" +
                (size != null ? size : "");
    }
}