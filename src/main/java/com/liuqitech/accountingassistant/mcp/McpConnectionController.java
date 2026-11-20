package com.liuqitech.accountingassistant.mcp;

import com.liuqitech.accountingassistant.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mcp/connections")
public class McpConnectionController {
    private final McpConnectionService connections;

    public McpConnectionController(McpConnectionService connections) { this.connections = connections; }

    @ModelAttribute
    public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @GetMapping
    public ApiResponse<List<McpConnectionService.Connection>> list(Authentication user) {
        return ApiResponse.success(connections.list(user.getName()));
    }

    @PostMapping
    public ApiResponse<McpConnectionService.Issued> create(Authentication user,
            @Valid @RequestBody McpConnectionService.CreateRequest request) {
        return ApiResponse.success(connections.create(user.getName(), request));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<Void> revoke(Authentication user, @PathVariable String id) {
        connections.revoke(user.getName(), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/rotate")
    public ApiResponse<McpConnectionService.Issued> rotate(Authentication user, @PathVariable String id) {
        return ApiResponse.success(connections.rotate(user.getName(), id));
    }

    @GetMapping("/{id}/calls")
    public ApiResponse<List<McpConnectionService.CallLog>> calls(Authentication user, @PathVariable String id) {
        return ApiResponse.success(connections.calls(user.getName(), id));
    }
}
