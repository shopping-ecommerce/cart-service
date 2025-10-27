package iuh.fit.se.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.Map;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddToCartRequest {
    @NotBlank(message = "Product ID is required")
    String productId;

    @NotBlank(message = "User ID is required")
    String userId;
    @NotBlank
    String sellerId;
    @NotBlank
    String sellerName;
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 5, message = "Quantity cannot exceed 5")
    Integer quantity;
    Map<String,String> options;
}
