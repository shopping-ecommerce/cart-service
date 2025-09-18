package iuh.fit.se.service.impl;

import iuh.fit.se.dto.request.AddToCartRequest;
import iuh.fit.se.dto.response.CartItemSummaryResponse;
import iuh.fit.se.dto.response.CartSummaryResponse;
import iuh.fit.se.dto.response.SellerSummaryResponse;
import iuh.fit.se.entity.Cart;
import iuh.fit.se.entity.CartItem;
import iuh.fit.se.exception.AppException;
import iuh.fit.se.exception.ErrorCode;
import iuh.fit.se.repository.CartRepository;
import iuh.fit.se.service.CartService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {
    CartRepository cartRepository;

    // Constants
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = BigDecimal.valueOf(500000);
    private static final BigDecimal SHIPPING_FEE = BigDecimal.valueOf(30000);

    @Override
    public Cart addToCart(AddToCartRequest request) {
        log.info("Adding item to cart for user: {}", request.getUserId());

        Cart cart = getOrCreateCart(request.getUserId());

        CartItem newItem = CartItem.builder()
                .productId(request.getProductId())
                .sellerId(request.getSellerId())
                .size(request.getSize())
                .unitPrice(request.getUnitPrice())
                .quantity(request.getQuantity())
                .build();
        newItem.calculateTotalPrice();

        // Check if item already exists
        String uniqueKey = newItem.getUniqueKey();
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getUniqueKey().equals(uniqueKey))
                .findFirst();

        if (existingItem.isPresent()) {
            // Update quantity
            CartItem existing = existingItem.get();
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
            existing.calculateTotalPrice();
            log.info("Updated existing item quantity to: {}", existing.getQuantity());
        } else {
            // Add new item
            cart.getItems().add(newItem);
            log.info("Added new item to cart: {}", newItem.getProductId());
        }

        cart.calculateTotals();
        return cartRepository.save(cart);
    }

    @Override
    public Cart getCartByUserId(String userId) {
        log.info("Getting cart for user: {}", userId);

        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public Cart getOrCreateCart(String userId) {
        log.info("Getting or creating cart for user: {}", userId);

        return cartRepository.findById(userId)
                .orElse(Cart.builder()
                        .id(userId)
                        .userId(userId)
                        .createdAt(LocalDateTime.now())
                        .build());
    }

    @Override
    public Cart updateCartItem(String userId, String productId, String sellerId, String size, String color, Integer quantity) {
        log.info("Updating cart item for user: {}", userId);

        Cart cart = getCartByUserId(userId);

        String uniqueKey = createUniqueKey(sellerId, productId, size, color);
        CartItem item = cart.getItems().stream()
                .filter(cartItem -> cartItem.getUniqueKey().equals(uniqueKey))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND ));

        if (quantity <= 0) {
            cart.getItems().remove(item);
            log.info("Removed item from cart: {}", productId);
        } else {
            item.setQuantity(quantity);
            item.calculateTotalPrice();
            log.info("Updated item quantity to: {}", quantity);
        }

        cart.calculateTotals();
        return cartRepository.save(cart);
    }

    @Override
    public Cart removeCartItem(String userId, String productId, String sellerId, String size, String color) {
        log.info("Removing cart item for user: {}", userId);

        Cart cart = getCartByUserId(userId);

        String uniqueKey = createUniqueKey(sellerId, productId, size, color);
        boolean removed = cart.getItems().removeIf(item -> item.getUniqueKey().equals(uniqueKey));

        if (!removed) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        cart.calculateTotals();
        return cartRepository.save(cart);
    }

    @Override
    public Cart clearCart(String userId) {
        log.info("Clearing cart for user: {}", userId);

        Cart cart = getCartByUserId(userId);
        cart.getItems().clear();
        cart.calculateTotals();

        return cartRepository.save(cart);
    }

    @Override
    public CartSummaryResponse getCartSummary(String userId) {
        log.info("Getting cart summary for user: {}", userId);

        Cart cart = getOrCreateCart(userId);

        if (cart.getItems().isEmpty()) {
            return CartSummaryResponse.builder()
                    .totalItems(0)
                    .totalSellers(0)
                    .subtotal(BigDecimal.ZERO)
                    .totalShipping(BigDecimal.ZERO)
                    .totalDiscount(BigDecimal.ZERO)
                    .finalAmount(BigDecimal.ZERO)
                    .hasOutOfStockItems(false)
                    .canCheckout(false)
                    .checkoutMessage("Cart is empty")
                    .build();
        }

        // Group items theo seller
        Map<String, List<CartItem>> itemsBySeller = cart.getItems().stream()
                .collect(Collectors.groupingBy(CartItem::getSellerId));

        List<SellerSummaryResponse> sellerSummaries = itemsBySeller.entrySet().stream()
                .map(entry -> {
                    String sellerId = entry.getKey();
                    List<CartItem> items = entry.getValue();

                    // Tính subtotal của seller
                    BigDecimal sellerSubtotal = items.stream()
                            .map(CartItem::getTotalPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    boolean freeShipping = sellerSubtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0;
                    BigDecimal shippingFee = freeShipping ? BigDecimal.ZERO : SHIPPING_FEE;
                    BigDecimal amountForFreeShipping = freeShipping ? BigDecimal.ZERO :
                            FREE_SHIPPING_THRESHOLD.subtract(sellerSubtotal);

                    // Map từng CartItem thành CartItemSummaryResponse
                    List<CartItemSummaryResponse> itemSummaries = items.stream()
                            .map(i -> CartItemSummaryResponse.builder()
                                    .productId(i.getProductId())
                                    .productName("Tên sản phẩm " + i.getProductId()) // TODO: call product-service
                                    .quantity(i.getQuantity())
                                    .unitPrice(i.getUnitPrice())
                                    .totalPrice(i.getTotalPrice())
                                    .size(i.getSize())
                                    .color(i.getColor())
                                    .build()
                            )
                            .toList();

                    return SellerSummaryResponse.builder()
                            .sellerId(sellerId)
                            .sellerName("Seller " + sellerId) // TODO: call seller-service nếu cần
                            .itemCount(items.size())
                            .subtotal(sellerSubtotal)
                            .shippingFee(shippingFee)
                            .freeShipping(freeShipping)
                            .amountForFreeShipping(amountForFreeShipping)
                            .items(itemSummaries)   // 👈 add items
                            .build();
                })
                .toList();

        BigDecimal totalShipping = sellerSummaries.stream()
                .map(SellerSummaryResponse::getShippingFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartSummaryResponse.builder()
                .totalItems(cart.getTotalItems())
                .totalSellers(itemsBySeller.size())
                .subtotal(cart.getSubtotal())
                .totalShipping(totalShipping)
                .totalDiscount(cart.getTotalDiscount())
                .finalAmount(cart.getSubtotal().add(totalShipping).subtract(cart.getTotalDiscount()))
                .sellerSummaries(sellerSummaries)
                .hasOutOfStockItems(false)
                .canCheckout(true)
                .checkoutMessage("Ready to checkout")
                .build();
    }



    @Override
    public int getCartItemCount(String userId) {
        log.info("Getting cart item count for user: {}", userId);

        return cartRepository.findByUserId(userId)
                .map(Cart::getTotalItems)
                .orElse(0);
    }

    private String createUniqueKey(String sellerId, String productId, String size, String color) {
        return (sellerId != null ? sellerId : "") + "-" +
                productId + "-" +
                (size != null ? size : "") + "-" +
                (color != null ? color : "");
    }
}