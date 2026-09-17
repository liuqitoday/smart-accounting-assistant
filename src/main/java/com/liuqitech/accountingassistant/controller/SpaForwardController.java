package com.liuqitech.accountingassistant.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({
            "/",
            "/login",
            "/dashboard",
            "/transactions",
            "/statistics",
            "/accounts",
            "/tags",
            "/ledgers",
            "/profile"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}
