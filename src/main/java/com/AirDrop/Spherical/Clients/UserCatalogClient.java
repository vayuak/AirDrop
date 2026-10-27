package com.AirDrop.Spherical.Clients;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
        name = "${user.catalog.service.name}",
        url = "${user.catalog.service.url}",
        path = "/api/users",
        configuration = FeignInterceptorConfig.class)
public interface UserCatalogClient {
    @PostMapping("/internal/block/{username}")
    void blockInternalUser(@PathVariable("username") String username);

    @GetMapping("/internal/token/{username}")      // was "/api/users/internal/token/{username}"
    String fetchTokenFromUserCatalog(@PathVariable("username") String username);
}
