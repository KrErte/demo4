package com.example.mcp.payment.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PaymentRedirectController {

    /**
     * Redirect success back to main page with session_id for status check.
     */
    @GetMapping("/payment/success")
    public String paymentSuccess(@RequestParam("session_id") String sessionId) {
        return "redirect:/?session_id=" + sessionId;
    }

    /**
     * Redirect cancel back to main page with session_id.
     */
    @GetMapping("/payment/cancel")
    public String paymentCancel(@RequestParam("session_id") String sessionId) {
        return "redirect:/?session_id=" + sessionId;
    }
}
