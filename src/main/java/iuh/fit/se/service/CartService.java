package iuh.fit.se.service;

import iuh.fit.se.dto.request.AddToCartRequest;
import iuh.fit.se.dto.response.CartItemSummaryResponse;
import iuh.fit.se.dto.response.CartSummaryResponse;
import iuh.fit.se.entity.Cart;

public interface CartService {
    Cart addToCart(AddToCartRequest request);

    Cart getCartByUserId(String userId);

    Cart getOrCreateCart(String userId);

    Cart updateCartItem(String userId, String productId, String sellerId, String size, String color, Integer quantity);

    Cart removeCartItem(String userId, String productId, String sellerId, String size, String color);

    Cart clearCart(String userId);

    CartSummaryResponse getCartSummary(String userId);

    int getCartItemCount(String userId);
}