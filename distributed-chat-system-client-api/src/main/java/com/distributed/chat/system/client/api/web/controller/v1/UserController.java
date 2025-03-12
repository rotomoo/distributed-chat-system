package com.distributed.chat.system.client.api.web.controller.v1;

import com.distributed.chat.system.client.api.web.service.UserService;
import com.distributed.chat.system.common.response.ResponseData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/private/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/my-info")
    public ResponseEntity<?> getMyInfo() {
        ResponseData responseData = userService.getMyInfo();

        return new ResponseEntity<>(responseData, HttpStatus.OK);
    }
}
