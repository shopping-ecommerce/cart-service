package iuh.fit.se.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
public class CartItemRemoveRequest {
    String sellerId;
    String productId;
    String size;
}
