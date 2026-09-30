package com.orbit.auth;

import org.springframework.stereotype.Service;

@Service
public class AccountService {
    private final AccountSecurityService security;
    public AccountService(AccountSecurityService security) { this.security = security; }
    public CurrentUser.UserView register(String name, String email, String password) {
        return security.register(name,email,password);
    }
}
