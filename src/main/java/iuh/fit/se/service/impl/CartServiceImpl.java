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
import java.util.*;
import java.util.stream.Collectors;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {
    CartRepository cartRepository;
    ProductClient productClient;

    // Constants
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = BigDecimal.valueOf(500_000);
    private static final BigDecimal SHIPPING_FEE = BigDecimal.valueOf(30_000);

    @Override
    public Cart addToCart(AddToCartRequest request) {
        log.info("Adding item to cart for user: {}; {}", request.getUserId(), request.getSellerId());

        Cart cart = getOrCreateCart(request.getUserId());

        // Lấy thông tin sản phẩm/biến thể theo OPTIONS
        ApiResponse<OrderItemProductResponse> productResponse =
                productClient.searchBySizeAndID(
                        SearchSizeAndIDRequest.builder()
                                .id(request.getProductId())
                                .options(request.getOptions())
                                .build()
                );
        log.info("Product response: {}", productResponse.getResult());
        if (productResponse == null || productResponse.getResult() == null) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        OrderItemProductResponse p = productResponse.getResult();

        CartItem newItem = CartItem.builder()
                .productId(request.getProductId())
                .sellerId(request.getSellerId())
                .sellerName(request.getSellerName())
                .options(request.getOptions())  // << sử dụng options
                .unitPrice(p.getPrice())
                .productImage(p.getImage())
                .productName(p.getName())
                .quantity(request.getQuantity())
                .build();

        newItem.calculateTotalPrice();
        log.info("New item details: {}", newItem);

        // So khớp item trùng (cùng seller + product + options)
        String uniqueKey = newItem.getUniqueKey();
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getUniqueKey().equals(uniqueKey))
                .findFirst();

        if (existingItem.isPresent()) {
            CartItem existing = existingItem.get();
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
            // cập nhật info mới nhất
            existing.setProductImage(newItem.getProductImage());
            existing.setProductName(newItem.getProductName());
            existing.setSellerName(newItem.getSellerName());
            existing.calculateTotalPrice();
            log.info("Updated existing item quantity to: {}", existing.getQuantity());
        } else {
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
                        .items(new ArrayList<>()) // đảm bảo không null
                        .build());
    }

    @Override
    public Cart updateCartItem(UpdateCartItemRequest request) {
        log.info("Updating cart item for user: {}", request.getUserId());

        Cart cart = getCartByUserId(request.getUserId());

        // UNIQUE theo options
        String uniqueKey = createUniqueKey(request.getSellerId(), request.getProductId(), request.getOptions());
        CartItem item = cart.getItems().stream()
                .filter(ci -> ci.getUniqueKey().equals(uniqueKey))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            cart.getItems().remove(item);
            log.info("Removed item from cart");
        } else {
            item.setQuantity(request.getQuantity());
            item.calculateTotalPrice();
            log.info("Updated item quantity to: {}", request.getQuantity());
        }

        cart.calculateTotals();
        return cartRepository.save(cart);
    }

    @Override
    public Cart removeCartItem(String userId, String productId, String sellerId, Map<String,String> options) {
        log.info("Removing cart item for user: {}", userId);

        Cart cart = getCartByUserId(userId);

        // Tạo key duy nhất theo seller + product + options
        String key = createUniqueKey(sellerId, productId, options == null ? Collections.emptyMap() : options);

        boolean removed = cart.getItems().removeIf(item -> item.getUniqueKey().equals(key));

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

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
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

        // group theo seller
        Map<String, List<CartItem>> itemsBySeller = cart.getItems().stream()
                .collect(Collectors.groupingBy(CartItem::getSellerId));

        List<SellerSummaryResponse> sellerSummaries = itemsBySeller.entrySet().stream()
                .map(entry -> {
                    String sellerId = entry.getKey();
                    List<CartItem> items = entry.getValue();

                    // subtotal theo seller
                    BigDecimal sellerSubtotal = items.stream()
                            .map(CartItem::getTotalPrice)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    boolean freeShipping = sellerSubtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0;
                    BigDecimal shippingFee = freeShipping ? BigDecimal.ZERO : SHIPPING_FEE;
                    BigDecimal amountForFreeShipping = freeShipping
                            ? BigDecimal.ZERO
                            : FREE_SHIPPING_THRESHOLD.subtract(sellerSubtotal);

                    // map từng item (KHÔNG còn field size)
                    List<CartItemSummaryResponse> itemSummaries = items.stream()
                            .map(i -> CartItemSummaryResponse.builder()
                                    .productId(i.getProductId())
                                    .productName(i.getProductName())
                                    .quantity(i.getQuantity())
                                    .productImage(i.getProductImage())
                                    .unitPrice(i.getUnitPrice())
                                    .totalPrice(i.getTotalPrice())
                                    .options(i.getOptions())   // chỉ trả về options
                                    .build()
                            )
                            .toList();

                    String sellerName = (items.isEmpty() || items.get(0).getSellerName() == null)
                            ? "Unknown seller"
                            : items.get(0).getSellerName();

                    return SellerSummaryResponse.builder()
                            .sellerId(sellerId)
                            .sellerName(sellerName)
                            .itemCount(items.size())
                            .subtotal(sellerSubtotal)
                            .shippingFee(shippingFee)
                            .freeShipping(freeShipping)
                            .amountForFreeShipping(amountForFreeShipping)
                            .items(itemSummaries)
                            .build();
                })
                .toList();

        BigDecimal totalShipping = sellerSummaries.stream()
                .map(SellerSummaryResponse::getShippingFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartSummaryResponse.builder()
                .totalItems(cart.getTotalItems())
                .totalSellers(itemsBySeller.size())
                .subtotal(cart.getSubtotal() == null ? BigDecimal.ZERO : cart.getSubtotal())
                .totalShipping(totalShipping)
                .totalDiscount(cart.getTotalDiscount() == null ? BigDecimal.ZERO : cart.getTotalDiscount())
                .finalAmount(
                        (cart.getSubtotal() == null ? BigDecimal.ZERO : cart.getSubtotal())
                                .add(totalShipping)
                                .subtract(cart.getTotalDiscount() == null ? BigDecimal.ZERO : cart.getTotalDiscount())
                )
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

        // Tạo list uniqueKey từ request (đã chuyển sang OPTIONS)
        List<String> uniqueKeysToRemove = request.getItems().stream()
                .map(item -> createUniqueKey(item.getSellerId(), item.getProductId(), item.getOptions()))
                .toList();

        boolean removedAny = cart.getItems().removeIf(ci -> uniqueKeysToRemove.contains(ci.getUniqueKey()));
        if (!removedAny) {
            throw new AppException(ErrorCode.SELLER_NOT_FOUND);
        }

        log.info("Removed batch items");
        cart.calculateTotals();
        return cartRepository.save(cart);
    }

    /* ================= Helpers ================= */

    private String createUniqueKey(String sellerId, String productId, Map<String, String> options) {
        CartItem tmp = CartItem.builder()
                .sellerId(sellerId)
                .productId(productId)
                .options(options)
                .build();
        return tmp.getUniqueKey();
    }
}
