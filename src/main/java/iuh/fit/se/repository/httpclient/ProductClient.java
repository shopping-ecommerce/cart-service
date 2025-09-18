package iuh.fit.se.repository.httpclient;


import iuh.fit.se.configuration.AuthenticationRequestInterceptor;
import iuh.fit.se.dto.request.SearchSizeAndIDRequest;
import iuh.fit.se.dto.response.ApiResponse;
import iuh.fit.se.dto.response.OrderItemProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-service", configuration = {AuthenticationRequestInterceptor.class})
public interface ProductClient {
    @PostMapping(value = "/searchBySizeAndID")
    ApiResponse<OrderItemProductResponse> searchBySizeAndID( @RequestBody SearchSizeAndIDRequest request);
}