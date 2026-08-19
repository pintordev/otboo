package com.sprint.mission.otboo.external.purchase;

import java.net.URI;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "purchasePageClient", url = "https://placeholder.invalid")
public interface PurchasePageClient {

  @GetMapping
  String fetchPage(URI uri);
}
