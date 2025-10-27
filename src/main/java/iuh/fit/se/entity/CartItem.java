package iuh.fit.se.entity;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CartItem {
    String productId;
    String sellerId;
    String sellerName;
    Map<String,String> options;
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
                (productId != null ? productId : "") + "-" +
                canonicalizeOptions(options);
    }

    // Chuẩn hoá options: sort theo key & nối "k=v|k2=v2"
    private static String canonicalizeOptions(Map<String,String> opts) {
        if (opts == null || opts.isEmpty()) return "";
        // sort by key để ổn định
        Map<String,String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sorted.putAll(opts);
        return sorted.entrySet().stream()
                .map(e -> (e.getKey() == null ? "" : e.getKey().trim()) + "=" + (e.getValue() == null ? "" : e.getValue().trim()))
                .collect(Collectors.joining("|"));
    }
}