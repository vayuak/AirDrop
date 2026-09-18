package com.AirDrop.Spherical.Clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
// 🟢 Strict enforcement: Requires properties to be set
@FeignClient(
        name = "${user.catalog.service.name}",
        url = "${user.catalog.service.url}",
        path = "/api/users",
        configuration = FeignInterceptorConfig.class)
public interface UserCatalogClient {
    @PostMapping("/internal/block/{username}")
    void blockInternalUser(@PathVariable("username") String username);
}