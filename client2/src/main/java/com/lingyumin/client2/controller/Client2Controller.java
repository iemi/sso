package com.lingyumin.client2.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [简要描述]:
 * [详细描述]:
 *
 * @author:
 * @date: 11:44 PM 2019/11/11
 * @since: JDK 1.8
 */
@RestController
public class Client2Controller {

    @GetMapping("/high")
    @PreAuthorize("hasAuthority('ROLE_HIGH')")
    public String normal( ) {
        return "high permission";
    }

    @GetMapping("/mid")
    @PreAuthorize("hasAuthority('ROLE_MID')")
    public String medium() {
        return "mid permission";
    }

    @GetMapping("/low")
    @PreAuthorize("hasAuthority('ROLE_LOW')")
    public String admin() {
        return "low permission";
    }

}
