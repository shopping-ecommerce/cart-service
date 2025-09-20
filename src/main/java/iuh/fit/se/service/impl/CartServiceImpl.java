package iuh.fit.se.service.impl;

import iuh.fit.se.dto.request.AddToCartRequest;
import iuh.fit.se.dto.request.RemoveCartItemsRequest;
import iuh.fit.se.dto.request.SearchSizeAndIDRequest;
import iuh.fit.se.dto.request.UpdateCartItemRequest;
import iuh.fit.se.dto.response.*;
import iuh.fit.se.entity.Cart;
import iuh.fit.se.entity.CartItem;
import iuh.fit.se.exception.AppException;
import iuh.fit.se.exception.ErrorCode;
import iuh.fit.se.repository.CartRepository;
import iuh.fit.se.repository.httpclient.ProductClient;
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
    ProductClient productClient;
    // Constants
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = BigDecimal.valueOf(500000);
    private static final BigDecimal SHIPPING_FEE = BigDecimal.valueOf(30000);

    @Override
    public Cart addToCart(AddToCartRequest request) {
        log.info("Adding item to cart for user: {}; {}", request.getUserId(),request.getSellerId());

        Cart cart = getOrCreateCart(request.getUserId());
        ApiResponse<OrderItemProductResponse> productResponse = productClient.searchBySizeAndID(SearchSizeAndIDRequest.builder()
                        .size(request.getSize())
                        .id(request.getProductId())
                .build());
        CartItem newItem;
            newItem = CartItem.builder()
                    .productId(request.getProductId())
                    .sellerId(request.getSellerId())
                    .sellerName(request.getSellerName())
                    .size(request.getSize())
                    .unitPrice(productResponse.getResult().getPrice())
                    .productImage(productResponse.getResult().getImage())
                    .productName(productResponse.getResult().getName())
                    .quantity(request.getQuantity())
                    .build();

        newItem.calculateTotalPrice();
        log.info("New item details: {}", newItem);
        // Check if item already exists
        String uniqueKey = newItem.getUniqueKey();
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getUniqueKey().equals(uniqueKey))
                .findFirst();

        if (existingItem.isPresent()) {
            // Update quantity
            CartItem existing = existingItem.get();
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
            existing.setProductImage(newItem.getProductImage()); // Cập nhật hình ảnh mới nhất
            existing.setProductName(newItem.getProductName()); // Cập nhật tên sản phẩm mới nhất
            existing.setSellerName(newItem.getSellerName()); // Cập nhật tên người bán mới nhất
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
    public Cart updateCartItem(UpdateCartItemRequest request) {
        log.info("Updating cart item for user: {}", request.getUserId());

        Cart cart = getCartByUserId(request.getUserId());

        String uniqueKey = createUniqueKey(request.getSellerId(), request.getProductId(), request.getSize());
        CartItem item = cart.getItems().stream()
                .filter(cartItem -> cartItem.getUniqueKey().equals(uniqueKey))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND ));

        if (request.getQuantity() <= 0) {
            cart.getItems().remove(item);
            log.info("Removed item from cart: {}", request.getQuantity());
        } else {
            item.setQuantity(request.getQuantity());
            item.calculateTotalPrice();
            log.info("Updated item quantity to: {}", request.getQuantity());
        }

        cart.calculateTotals();
        return cartRepository.save(cart);
    }

    @Override
    public Cart removeCartItem(String userId, String productId, String sellerId, String size) {
        log.info("Removing cart item for user: {}", userId);

        Cart cart = getCartByUserId(userId);

        String uniqueKey = createUniqueKey(sellerId, productId, size);
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
                                    .productName(i.getProductName())
                                    .quantity(i.getQuantity())
                                    .productImage(i.getProductImage())
                                    .unitPrice(i.getUnitPrice())
                                    .totalPrice(i.getTotalPrice())
                                    .size(i.getSize())
                                    .build()
                            )
                            .toList();

                    return SellerSummaryResponse.builder()
                            .sellerId(sellerId)
                            .sellerName(items.get(0).getSellerName()) // TODO: call seller-service nếu cần
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

    @Override
    public Cart removeCartItemsBatch(String userId, RemoveCartItemsRequest request) {
        log.info("Removing batch cart items for user: {}", userId);

        Cart cart = getCartByUserId(userId);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        // Tạo list uniqueKey từ request
        List<String> uniqueKeysToRemove = request.getItems().stream()
                .map(item -> createUniqueKey(item.getSellerId(), item.getProductId(), item.getSize()))
                .collect(Collectors.toList());

        // Xóa tất cả item matching uniqueKey trong list
        boolean removedCount = cart.getItems().removeIf(item -> uniqueKeysToRemove.contains(item.getUniqueKey()));

        if (!removedCount) {
            throw new AppException(ErrorCode.SELLER_NOT_FOUND);
        }

        log.info("Removed {} items from cart", removedCount);
        cart.calculateTotals();
        return cartRepository.save(cart);
    }

    private String createUniqueKey(String sellerId, String productId, String size) {
        return (sellerId != null ? sellerId : "") + "-" +
                productId + "-" +
                (size != null ? size : "");
    }
}