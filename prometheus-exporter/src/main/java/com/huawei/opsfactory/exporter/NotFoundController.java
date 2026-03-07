package com.huawei.opsfactory.exporter;

import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotFoundController {

    @RequestMapping("/**")
    public ResponseEntity<String> notFound(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/".equals(path) || "/metrics".equals(path) || "/health".equals(path)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.TEXT_PLAIN)
            .body("Not Found\n");
    }
}
