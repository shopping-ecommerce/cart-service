package iuh.fit.se.controller;

import iuh.fit.se.dto.request.AddToCartRequest;
import iuh.fit.se.dto.request.RemoveCartItemsRequest;
import iuh.fit.se.dto.request.UpdateCartItemRequest;
import iuh.fit.se.dto.response.ApiResponse;
import iuh.fit.se.dto.response.CartItemSummaryResponse;
import iuh.fit.se.dto.response.CartSummaryResponse;
import iuh.fit.se.entity.Cart;
import iuh.fit.se.service.CartService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartController {
    CartService cartService;

    @PostMapping("/add")
    public ApiResponse<Cart> addToCart(@Valid @RequestBody AddToCartRequest addToCartRequest) {
        return ApiResponse.<Cart>builder()
                .code(200)
                .message("Product added to cart successfully")
                .result(cartService.addToCart(addToCartRequest))
                .build();
    }

    @GetMapping("/{userId}")
    public ApiResponse<Cart> getCartByUserId(@PathVariable("userId") String userId) {
        return ApiResponse.<Cart>builder()
                .code(200)
                .message("Cart retrieved successfully")
                .result(cartService.getOrCreateCart(userId))
                .build();
    }

    @GetMapping("/{userId}/summary")
    public ApiResponse<CartSummaryResponse> getCartSummary(@PathVariable("userId") String userId) {
        return ApiResponse.<CartSummaryResponse>builder()
                .code(200)
                .message("Cart summary retrieved successfully")
                .result(cartService.getCartSummary(userId))
                .build();
    }

    @GetMapping("/{userId}/count")
    public ApiResponse<Integer> getCartItemCount(@PathVariable("userId") String userId) {
        return ApiResponse.<Integer>builder()
                .code(200)
                .message("Cart item count retrieved successfully")
                .result(cartService.getCartItemCount(userId))
                .build();
    }

    @PutMapping("/{userId}/update")
    public ApiResponse<Cart> updateCartItem(@Valid @RequestBody UpdateCartItemRequest request) {

        return ApiResponse.<Cart>builder()
                .code(200)
                .message("Cart item updated successfully")
                .result(cartService.updateCartItem(request))
                .build();
    }

    @DeleteMapping("/{userId}/items/{productId}")
    public ApiResponse<Cart> removeCartItem(
            @PathVariable("userId") String userId,
            @PathVariable("productId") String productId,
            @RequestParam("sellerId") String sellerId,
            @RequestParam(value = "size", required = false) String size) {

        return ApiResponse.<Cart>builder()
                .code(200)
                .message("Cart item removed successfully")
                .result(cartService.removeCartItem(userId, productId, sellerId, size))
                .build();
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Cart> clearCart(@PathVariable("userId") String userId) {
        return ApiResponse.<Cart>builder()
                .code(200)
                .message("Cart cleared successfully")
                .result(cartService.clearCart(userId))
                .build();
    }

    @DeleteMapping("/{userId}/items/batch")
    public ApiResponse<Cart> removeCartItemsBatch(
            @PathVariable("userId") String userId,
            @RequestBody RemoveCartItemsRequest request) {  // Nhận body JSON với list

        return ApiResponse.<Cart>builder()
                .code(200)
                .message("Cart items removed successfully")
                .result(cartService.removeCartItemsBatch(userId, request))
                .build();
    }

}