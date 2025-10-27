package iuh.fit.se.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
public class CartItemRemoveRequest {
    String sellerId;
    String productId;
    Map<String,String> options;
}
