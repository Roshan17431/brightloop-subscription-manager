package com.brightloop.modules.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.brightloop.modules.audit.AuditService;
import com.brightloop.modules.user.*;
import com.brightloop.security.CurrentUser;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductVisibilityTest {
  private ProductService catalog(Role role) {
    var product = new Product();
    product.id = 1L;
    product.name = "Business software";
    product.salesPrice = new BigDecimal("100.00");
    product.costPrice = new BigDecimal("40.00");
    var repository = mock(ProductRepository.class);
    when(repository.findById(1L)).thenReturn(Optional.of(product));
    var current = mock(CurrentUser.class);
    var account = new UserAccount();
    account.role = role;
    when(current.get()).thenReturn(account);
    return new ProductService(repository, current, mock(AuditService.class));
  }

  @Test
  void customerCatalogOmitsInternalCost() {
    var product = catalog(Role.CUSTOMER).get(1L);
    assertNull(product.costPrice());
    assertEquals(new BigDecimal("100.00"), product.salesPrice());
  }

  @Test
  void managersRetainInternalCost() {
    for (var role : new Role[] {Role.ADMIN, Role.STAFF}) {
      assertEquals(new BigDecimal("40.00"), catalog(role).get(1L).costPrice());
    }
  }
}
