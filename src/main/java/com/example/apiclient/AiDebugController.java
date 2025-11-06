package com.example.apiclient;
// e.g., AiDebugController.java

import com.example.apiclient.ai.NaturalLanguageRouter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AiDebugController {
    private final NaturalLanguageRouter nl;
    private final CommandRouter router;
    AiDebugController(NaturalLanguageRouter nl, CommandRouter router){ this.nl = nl; this.router = router; }

    @GetMapping("/ai/debug")
    public String debug(@RequestParam String q) {
        String cmd = nl.toCommand(q);
        String out = router.handle(cmd).block();
        return "cmd=" + cmd + "\n\n" + out;
    }
}

