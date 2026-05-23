/*
package com.anvistudio.boutique.controller;

import com.anvistudio.boutique.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

*/
/**
 * Handles incoming requests from the email verification link.
 *//*

@Controller
public class VerificationController {

    private final UserService userService;

    public VerificationController(UserService userService) {
        this.userService = userService;
    }

    */
/**
     * Endpoint that handles the click on the email verification link.
     * URL: /verify?token={token_value}
     *//*

    @GetMapping("/verify")
    public String verifyAccount(@RequestParam("token") String token, Model model) {

        String resultMessage = userService.confirmUserAccount(token);

        // Pass the result message back to the template
        model.addAttribute("verificationResult", resultMessage);

        // Maps to a new template to display the result (success or failure)
        return "verification_result";
    }
}*/
