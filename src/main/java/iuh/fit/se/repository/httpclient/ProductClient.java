package iuh.fit.se.repository.httpclient;


import iuh.fit.se.configuration.AuthenticationRequestInterceptor;
import iuh.fit.se.dto.request.SearchSizeAndIDRequest;
import iuh.fit.se.dto.response.ApiResponse;
import iuh.fit.se.dto.response.OrderItemProductResponse;
import iuh.fit.se.dto.response.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-service", configuration = {AuthenticationRequestInterceptor.class})
public interface ProductClient {
    @PostMapping(value = "/searchBySizeAndID")
    ApiResponse<OrderItemProductResponse> searchBySizeAndID( @RequestBody SearchSizeAndIDRequest request);

    @PostMapping(value= "/search")
    ApiResponse<ProductResponse> searchById(@RequestParam("id") String id);
}