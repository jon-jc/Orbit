package com.orbit;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:orbit-productivity;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password="})
class ProductivityTest extends ProductivityContract { }
