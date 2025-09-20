package iuh.fit.se.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
public class RemoveCartItemsRequest {
    List<CartItemRemoveRequest> items;
}
