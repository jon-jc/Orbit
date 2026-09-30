package com.orbit.auth;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:orbit-account-security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa","spring.datasource.password="})
class AccountSecurityTest extends AccountSecurityContract {}
